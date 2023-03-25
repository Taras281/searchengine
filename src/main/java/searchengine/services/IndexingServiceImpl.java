package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Label;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.config.UserAgent;
import searchengine.dto.responce.IndexingResponse;
import searchengine.dto.responce.IndexingResponseNotOk;
import searchengine.dto.responce.IndexingResponseOk;
import searchengine.model.Page;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.lemmatization.SubLemmatizatorController;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service

public class IndexingServiceImpl implements IndexingService {

    private SiteRepository siteRepository;

    private PageRepository pageReposytory;

    private UserAgent userAgent;

    private SitesList sitesList;

    private Logger logger;

    private SubLemmatizatorController subLemmatizatorController;

    private boolean indexingState;
    private ForkJoinPool forkJoinPool;
    private ParserForkJoin parserForkJoin;
    private ArrayList<ParserForkJoin> listTask;
    {
        indexingState = false;
    }

    private Label myLabel;
    public IndexingServiceImpl(SiteRepository siteRepository, PageRepository pageReposytory, UserAgent userAgent, SitesList sitesList, Label label, Logger logger, SubLemmatizatorController subLemmatizatorController) {
        this.siteRepository = siteRepository;
        this.pageReposytory = pageReposytory;
        this.sitesList = sitesList;
        this.myLabel = label;
        this.userAgent = userAgent;
        this.logger = logger;
        this.subLemmatizatorController = subLemmatizatorController;
    }

    public ResponseEntity<IndexingResponse> getStartResponse(){
        if (!indexingState){
            indexingState = true;
            startIndexing();
            return new ResponseEntity<>(createResponse(true, !indexingState), HttpStatus.OK);
        }
        return  new ResponseEntity<>(createResponse(true, indexingState), HttpStatus.BAD_REQUEST);
    }

    private IndexingResponse createResponse(boolean start, boolean indexingStarted){
        IndexingResponse indexingResponse=null;
        if(start&&!indexingStarted){
            return createIndexingResponseOk();
        }
        else if (start&&indexingStarted){
            return createResponseNotOk();
        }
        else if(!start&&indexingStarted){
            return createIndexingResponseOk();
        }
        else if (!start&&!indexingStarted){
            indexingResponse = createResponseNotOk();
        }
        return indexingResponse;
    }

    private IndexingResponse createIndexingResponseOk() {
        IndexingResponseOk indexingResponseOk =  new IndexingResponseOk();
        indexingResponseOk.setResult(true);
        return indexingResponseOk;
    }

    private IndexingResponse createResponseNotOk() {
        IndexingResponseNotOk indexingResponseNotOk = new IndexingResponseNotOk();
        indexingResponseNotOk.setResult(false);
        indexingResponseNotOk.setError(myLabel.getIndexingNotStarted());
        return indexingResponseNotOk;

    }

    public ResponseEntity<IndexingResponse> getStopIndexing() throws InterruptedException {
        if(indexingState){
            stopIndexing();
            indexingState = false;
            return new ResponseEntity<>(createResponse(false, !indexingState), HttpStatus.OK);
        }
        else {
            return new ResponseEntity<>(createResponse(false, indexingState), HttpStatus.BAD_REQUEST);
        }
    }

    private void startIndexing() {
        forkJoinPool =  new ForkJoinPool();
        listTask = new ArrayList<>();
        for(Site site: sitesList.getSites()){
            String url = site.getUrl();
            siteRepository.deleteByUrl(url);
            searchengine.model.Site siteInBase = returnModelSite(StatusEnum.INDEXING, getDataTime(), "", url, site.getName());
            synchronized (siteRepository){
                siteRepository.save(siteInBase);
            }
            Set notEyetParsingLinkWhereStopedUser = new HashSet();
            parserForkJoin = new ParserForkJoin(url, siteInBase, url, notEyetParsingLinkWhereStopedUser);
            parserForkJoin.blockWorkForkJoin=false;
            listTask.add(parserForkJoin);
            new Thread(()->{forkJoinPool.invoke(parserForkJoin);
                siteInBase.setStatus(StatusEnum.INDEXED);
                siteRepository.save(siteInBase);
                }).start();
        }
    }
    private void stopIndexing() throws InterruptedException {
        for(ParserForkJoin task: listTask){
            task.stopIndexing();
        }
        forkJoinPool.shutdownNow();
    }

    private searchengine.model.Site returnModelSite(StatusEnum indexing, String dataTime, String error, String url, String nameSite) {
        searchengine.model.Site site=siteRepository.findByUrl(url);
        if(site==null){
            site = new searchengine.model.Site();
            site.setStatus(indexing);
            site.setStatusTime(dataTime);
            site.setLastError(error);
            site.setUrl(url);
            site.setName(nameSite);
        }
        return site;
    }
    private String getDataTime() {
        Calendar cal = Calendar.getInstance();
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String result = formatter.format(cal.getTime());
        return result;
    }

        public class ParserForkJoin extends RecursiveTask<Set<String>>{
            private  Set<String> notEyetParsingLinkWhereStopedUser;
            String link;
            private String marker;
            private static Boolean blockWorkForkJoin;
            private searchengine.model.Site site;
            static private Set<String> errorLinks;

            public ParserForkJoin(String link, searchengine.model.Site site, String marker, Set notEyetParsingLinkWhereStopedUser) {
                this.link = link;
                this.marker = marker;
                this.notEyetParsingLinkWhereStopedUser = notEyetParsingLinkWhereStopedUser;
                if(errorLinks==null){
                    errorLinks = new HashSet<>();
                }
                this.site=site;
            }

            @Override
            protected Set<String> compute() {
                Set<String> resultSet = new HashSet<>();
                if(!blockWorkForkJoin){
                    resultSet.add(link);
                    Set<String> links = pars(link);
                    List<ParserForkJoin> parserTask = new ArrayList<>();
                    for(String l:links){
                        ParserForkJoin p = new ParserForkJoin(l, site, marker, notEyetParsingLinkWhereStopedUser);
                        p.fork();
                        parserTask.add(p);
                    }
                    for(ParserForkJoin p:parserTask){
                        resultSet.addAll(p.join());
                    }
                }
                return resultSet;
            }

            private Set<String> pars(String url){
                Set setUrlinPage = new HashSet<>();
                if ((!validUrl(url))||(!contaning(url, marker))|| errorLinks.contains(url)) {
                    notEyetParsingLinkWhereStopedUser.remove(url);
                    return setUrlinPage;
                }
                Document document = getDocument(url);
                if (document == (null)) {
                    notEyetParsingLinkWhereStopedUser.remove(url);
                    return setUrlinPage;
                }
                int statusResponse = document.connection().response().statusCode();
                synchronized (pageReposytory){
                    if (dontContained((url))) {
                        pageReposytory.save(new Page((int) (pageReposytory.count() + 1), site, url, statusResponse, document.head().toString()+document.body().toString()));
                        setTime(site);
                        startLematization(url);
                    }
                    else{
                        notEyetParsingLinkWhereStopedUser.remove(url);
                        return setUrlinPage;
                    }}
                setUrlinPage = getSetUrlInPage(document);
                if(!blockWorkForkJoin){
                    notEyetParsingLinkWhereStopedUser.addAll(setUrlinPage);
                    notEyetParsingLinkWhereStopedUser.remove(url);
                }
                return setUrlinPage;
            }

            private Set getSetUrlInPage(Document document) {
                Elements links = document.select("a[href]");
                Set<String> linkss= links.stream().map(link-> link.attr("abs:href")).collect(Collectors.toSet());
                return getSetLinkAfterCheckConsistBase(linkss).stream()
                        .filter(link->(contaning(link, marker) && validUrl(link) && !link.isEmpty())).collect(Collectors.toSet());
            }

            private Document getDocument(String url) {
                Document document = null;
                try {
                    document = Jsoup.connect(url)
                            .userAgent(userAgent.getUserAgent())
                            .referrer(userAgent.getReferrer())
                            .get();
                } catch (IOException e) {
                    synchronized (url){
                        String error = "Error pars url " + url + "  " +e.toString();
                        sendErrorBase(error);
                        errorLinks.add(url);
                        logger.error(error);
                    }
                }
                return document;
            }

            private void startLematization(String url) {
                subLemmatizatorController.addDeque(url);
            }


            public boolean contaning(String absLink, String marker) {
                if (marker.contains("www.")) {
                    String[] s = marker.split("www.");
                    if (s.length > 1) {
                        marker = s[0] + s[1];
                    } else {
                        marker = s[0];
                    }
                }
                if (absLink.contains("www.")) {
                    String[] s = absLink.split("www.");
                    if (s.length > 1) {
                        absLink = s[0] + s[1];
                    } else {
                        absLink = s[0];
                    }
                }
                return absLink.contains(marker);
            }

            private boolean validUrl(String url) {
                return url.matches("https?://.*")&&!url.matches(".*#")&&!url.matches(".*(jpg|jpeg|JPG|png|bmp|pdf|mp4|WebM|js)");
            }


            private void stopIndexing() throws InterruptedException {
                blockWorkForkJoin=true;
                synchronized (pageReposytory){
                    synchronized (notEyetParsingLinkWhereStopedUser){
                        for(String url: notEyetParsingLinkWhereStopedUser){
                            if(dontContained(url)){
                                pageReposytory.save(new Page((int)pageReposytory.count()+1, site, url,
                                        418, myLabel.getIndexingStopedUser()));
                            }
                        }
                    }
                }
                setStatusSiteIndexed(site);
            }

            private void setStatusSiteIndexed(searchengine.model.Site site){
                synchronized (siteRepository){
                    site.setStatus(StatusEnum.INDEXED);
                    siteRepository.save(site);
                }
            }

            private void setTime(searchengine.model.Site site) {
                synchronized (siteRepository){
                    site.setStatusTime(getDataTime());
                    siteRepository.save(site);
                }
            }
            private void sendErrorBase(String error){
                synchronized (siteRepository){
                    String err = error;
                    site.setLastError(err);
                    siteRepository.save(site);
                }
            }


            private boolean dontContained(String absLink) {
                Set<Page> response = pageReposytory.findAllByPathIn(Set.of(absLink));
                return response.isEmpty();
            }

            private Set<String> getSetLinkAfterCheckConsistBase(Set<String> inputSet){
                Set<Page> linkExistIntoBase = pageReposytory.findAllByPathIn(inputSet);
                for(Page s: linkExistIntoBase){
                    inputSet.remove(s.getPath());
                }
                return inputSet;
            }
        }
    }

