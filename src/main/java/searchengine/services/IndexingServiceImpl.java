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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
    public IndexingServiceImpl(SiteRepository siteRepository, PageRepository pageReposytory,
                               UserAgent userAgent, SitesList sitesList, Label label, Logger logger,
                               SubLemmatizatorController subLemmatizatorController) {
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
        return  new ResponseEntity<>(createResponse(true, indexingState), HttpStatus.OK);
    }

    private IndexingResponse createResponse(boolean start, boolean indexingStarted){
        IndexingResponse indexingResponse=null;
        if(start&&!indexingStarted){
            return createIndexingResponseOk();
        }
        else if (start&&indexingStarted){
            return createResponseNotOk(myLabel.getIndexingStarted());
        }
        else if(!start&&indexingStarted){
            return createIndexingResponseOk();
        }
        else if (!start&&!indexingStarted){
            indexingResponse = createResponseNotOk(myLabel.getIndexingNotStarted());
        }
        return indexingResponse;
    }

    private IndexingResponse createIndexingResponseOk() {
        IndexingResponseOk indexingResponseOk =  new IndexingResponseOk();
        indexingResponseOk.setResult(true);
        return indexingResponseOk;
    }

    private IndexingResponse createResponseNotOk(String error) {
        IndexingResponseNotOk indexingResponseNotOk = new IndexingResponseNotOk();
        indexingResponseNotOk.setResult(false);
        indexingResponseNotOk.setError(error);
        return indexingResponseNotOk;

    }

    public ResponseEntity<IndexingResponse> getStopIndexing() throws InterruptedException {
        if(indexingState){
            try{
                stopIndexing();
            }
            catch (ConcurrentModificationException cme){
                return new ResponseEntity<>(createResponseErrorBase(), HttpStatus.OK);
            }

            indexingState = false;
            return new ResponseEntity<>(createResponse(false, !indexingState), HttpStatus.OK);
        }
        else {
            return new ResponseEntity<>(createResponse(false, indexingState), HttpStatus.OK);
        }
    }

    private IndexingResponseNotOk createResponseErrorBase() {
        IndexingResponseNotOk indexingResponseNotOk = new IndexingResponseNotOk();
        indexingResponseNotOk.setResult(false);
        indexingResponseNotOk.setError("Ошибка остановки Индексации, но в целом все хорошо, нажмите обновить страницу");
        return indexingResponseNotOk;
    }

    private void startIndexing() {
        forkJoinPool =  new ForkJoinPool();
        listTask = new ArrayList<>();
        siteRepository.deleteAll();
        for(Site site: sitesList.getSites()) {
            String url = site.getUrl();
            searchengine.model.Site siteInBase = returnModelSite(StatusEnum.INDEXING, getDataTime(), "", url, site.getName());
            searchengine.model.Site siteFromBase=null;
            Set<String> errorLinks = new HashSet<>();
            CopyOnWriteArraySet<String> notEyetParsingLinkWhereStopedUser = new  CopyOnWriteArraySet<>();
            synchronized (siteRepository) {
                siteFromBase = siteRepository.save(siteInBase);
            }
            parserForkJoin =  new ParserForkJoin(url, url, siteFromBase, errorLinks, notEyetParsingLinkWhereStopedUser);
            ParserForkJoin.blockWorkForkJoin = false;
            listTask.add(parserForkJoin);
        }
            AtomicInteger counterStopedPfj= new AtomicInteger();
            for (ParserForkJoin pfj: listTask){
            new Thread(()->{
                forkJoinPool.invoke(pfj);
                counterStopedPfj.incrementAndGet();
                pfj.getSite().setStatus(StatusEnum.INDEXED);
                siteRepository.save(pfj.getSite());
                if(counterStopedPfj.get()==listTask.size()){
                        indexingState=false;
                }
                }).start();
        }
    }


    private void stopIndexing() throws InterruptedException, ConcurrentModificationException {
        for(ParserForkJoin task: listTask){
            task.stopIndexing();
        }
        subLemmatizatorController.shutdown();
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
            private   CopyOnWriteArraySet<String> notEyetParsingLinkWhereStopedUser;
            private String link;
            private static Boolean blockWorkForkJoin;
            private String marker;
            private searchengine.model.Site site;
            private Set<String> errorLinks;

            public ParserForkJoin(String link, String marker, searchengine.model.Site site, Set<String> errorLinks,  CopyOnWriteArraySet<String> notEyetParsingLinkWhereStopedUser) {
                this.link = link;
                this.marker=marker;
                this.site=site;
                this.errorLinks=errorLinks;
                this.notEyetParsingLinkWhereStopedUser = notEyetParsingLinkWhereStopedUser;
            }

            public searchengine.model.Site getSite(){
                return site;
            }


            @Override
            protected Set<String> compute() {
                Set<String> resultSet = new HashSet<>();
                if(!blockWorkForkJoin){
                        resultSet.add(link);
                        Set<String> links = pars(link);
                        List<ParserForkJoin> parserTask = new ArrayList<>();
                        for(String l:links){
                            ParserForkJoin p = new ParserForkJoin(l, marker, site, errorLinks, notEyetParsingLinkWhereStopedUser);
                            parserTask.add(p);
                            p.fork();
                        }
                        for(ParserForkJoin p:parserTask){
                            resultSet.addAll(p.join());
                        }
                    }
                return resultSet;
            }

            private synchronized Set<String> pars(String url){
                Set<String> setUrlinPage = new HashSet<>();
                Document document = getDocument(url);
                if (document == (null)) {
                    notEyetParsingLinkWhereStopedUser.remove(url);
                    errorLinks.add(url);
                    return setUrlinPage;
                }
                int statusResponse = document.connection().response().statusCode();
                    String urlForPath="";
                    try {
                        urlForPath = remoovePrefix(site,url);
                    } catch (ExceptionNotUrl e) {
                        return setUrlinPage;
                    }
                    Page page = null;
                    page = new Page(1, site, urlForPath,
                            statusResponse, document.head().toString()+document.body().toString());

                   if (dontContained((page)))
                    {
                        try {
                               synchronized (pageReposytory){
                                page = pageReposytory.save(page);
                            }
                        }
                        catch (Exception sqe){
                            logger.error("sqe "+ sqe);
                        }
                        setTime(site);
                        startLematization(page);
                    }
                    else{
                        notEyetParsingLinkWhereStopedUser.remove(url);
                        return setUrlinPage;
                    }
                    setUrlinPage = getSetUrlInPage(document);
                    setUrlinPage.add(url);
                    setUrlinPage = reduseList(setUrlinPage, site);

                if(!blockWorkForkJoin){
                    notEyetParsingLinkWhereStopedUser.addAll(setUrlinPage);
                    notEyetParsingLinkWhereStopedUser.remove(url);
                }
                return setUrlinPage;
            }

            private Set reduseList(Set<String> setUrlinPage, searchengine.model.Site site) {
                String prefix = site.getUrl();
                Set<String> urlSetWithoutPrefix = new HashSet<>();
                for(String url: setUrlinPage){
                    try {
                        urlSetWithoutPrefix.add(remoovePrefix(site, url));
                    } catch (ExceptionNotUrl e) {
                        continue;
                    }
                }
                Set<Page> pageInBase=null;
                try{
                    pageInBase = pageReposytory.findAllBySiteAndPathIn(site, urlSetWithoutPrefix);
                }
                catch (Exception ecp)
                {
                    logger.error("PAGEINBASE" + ecp);
                }

                Set<String> listUrlInBase = new HashSet<>();
                if(pageInBase.size()>0){
                    for(Page pageinbase: pageInBase){
                        listUrlInBase.add(pageinbase.getPath());
                    }
                }
                urlSetWithoutPrefix.removeAll(listUrlInBase);
                Set<String> result = new HashSet<>();
                for (String link: urlSetWithoutPrefix){
                    result.add(prefix+link);
                }
                return result;
            }

            private String remoovePrefix(searchengine.model.Site site, String url) throws ExceptionNotUrl {
                if (!url.contains(site.getUrl())) {
                        throw new ExceptionNotUrl();
                    }
                String result=url.replaceAll(site.getUrl(),"");
                return result;
            }

            private Set getSetUrlInPage(Document document) {
                Elements links = document.select("a[href]");
                Set<String> linkss= links.stream().map(link-> link.attr("abs:href")).collect(Collectors.toSet());
                Set<String> res = getSetLinkAfterCheckConsistBase(linkss).stream()
                        .filter(link->(contaning(link, marker) && validUrl(link)
                                && !link.isEmpty()&&!errorLinks.contains(link)&& !notEyetParsingLinkWhereStopedUser.contains(link)
                                && dontCrazyYear(link))).collect(Collectors.toSet());
                return res;
            }

            public boolean dontCrazyYear(String link) {
                String regex = ".+\\?.+year=\\d{4}.*";
                int year = LocalDateTime.now().getYear();
                String s=String.valueOf(year);
                if(link.matches(regex)){
                    int index= link.indexOf("year=")+5;
                    s = link.substring(index,index+4);

                }
                boolean res = Integer.parseInt(s)>year+1?false:true;
                boolean res2 = Integer.parseInt(s)<year-1?false:true;
                return (res&&res2);
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
                    }
                }
                return document;
            }

            private void startLematization(Page page) {
                subLemmatizatorController.addDeque(page);
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
                boolean res = url.matches("https?://.*")&&!url.matches(".*#")&&!url.matches(".*(jpg|jpeg|JPG|png|bmp|pdf|mp4|WebM|js)");
                return res;
            }


            private void stopIndexing() throws InterruptedException, ConcurrentModificationException {
                blockWorkForkJoin=true;
                synchronized (pageReposytory){
                    synchronized (notEyetParsingLinkWhereStopedUser){
                        for(String url: notEyetParsingLinkWhereStopedUser){
                            Page p=null;
                            try { p = new Page((int)pageReposytory.count()+1, site, remoovePrefix(site,url),
                                    418, myLabel.getIndexingStopedUser());
                            } catch (ExceptionNotUrl e) {
                                continue;
                            }
                            if(dontContained(p)){
                                    pageReposytory.save(p);
                                    sendErrorBase("Stopped indexing user");
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
            private boolean dontContained(Page page) {
                Page p=null;
                try{
                    p= pageReposytory.findByPathAndSite(page.getPath(), page.getSite());
                }
                catch (Exception ecp)
                {
                    logger.error("dontContained " + ecp);
                }
                    return p==null?true:false;
            }

            private Set<String> getSetLinkAfterCheckConsistBase(Set<String> inputSet){
                Set<Page> linkExistIntoBase=null;
                try{
                    linkExistIntoBase = pageReposytory.findAllByPathIn(inputSet);
                }
                catch (Exception e){
                    logger.error("Exc getSetLinkAfterCheckConsistBase" + e);
                }
                for(Page s: linkExistIntoBase){
                    inputSet.remove(s.getPath());
                }
                return inputSet;
            }
        }
    }

