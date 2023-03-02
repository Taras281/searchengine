package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.Label;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.config.USerAgent;
import searchengine.dto.responce.IndexingResponse;
import searchengine.dto.responce.IndexingResponseNotOk;
import searchengine.dto.responce.IndexingResponseOk;
import searchengine.model.Page;
import searchengine.model.StatusEnum;
import searchengine.reposytory.PageReposytory;
import searchengine.reposytory.SiteRepository;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service

public class IndexingServiceImpl implements IndexingService {

    private SiteRepository siteRepository;

    private PageReposytory pageReposytory;

    private SitesList sitesListFromProperties;

    private USerAgent uSerAgent;

    private SitesList sitesList;


    private boolean indexingState;
    private ArrayList<SubClassIndexingServise> listIndexingServiseImpl;
    private ExecutorService tpe;
    private SubClassIndexingServise iis;
    {
        indexingState = false;
    }
    private Label myLabel;
    public IndexingServiceImpl(SiteRepository siteRepository, PageReposytory pageReposytory, SitesList sitesListFromProperties, USerAgent uSerAgent, SitesList sitesList, Label label) {
        this.siteRepository = siteRepository;
        this.pageReposytory = pageReposytory;
        this.sitesListFromProperties = sitesListFromProperties;
        this.uSerAgent = uSerAgent;
        this.sitesList = sitesList;
        this.myLabel = label;
    }

    public ResponseEntity<IndexingResponse> getStartResponse(){
        if (!indexingState){
            indexingState = true;
            startIndexing();
            return new ResponseEntity<>(creatResponse(true, !indexingState), HttpStatus.OK);
        }
        else{

        }
        return  new ResponseEntity<>(creatResponse(true, indexingState), HttpStatus.BAD_REQUEST);
    }

    private IndexingResponse creatResponse(boolean start, boolean indexingStarted){
        IndexingResponseOk indexingResponseOk=null;
        IndexingResponseNotOk indexingResponseNotOk = null;
        if(start&&!indexingStarted){
            indexingResponseOk = new IndexingResponseOk();
            indexingResponseOk.setResult(true);
            return indexingResponseOk;
        }
        else if (start&&indexingStarted){
            indexingResponseNotOk = new IndexingResponseNotOk();
            indexingResponseNotOk.setResult(false);
            indexingResponseNotOk.setError(myLabel.getIndexingStarted());
            return indexingResponseNotOk;
        }
        else if(!start&&indexingStarted){
            indexingResponseOk = new IndexingResponseOk();
            indexingResponseOk.setResult(true);
            return indexingResponseOk;
        }
        else if (!start&&!indexingStarted){
            indexingResponseNotOk = new IndexingResponseNotOk();
            indexingResponseNotOk.setResult(false);
            indexingResponseNotOk.setError(myLabel.getIndexingNotStarted());
        }
        return indexingResponseNotOk;
    }

    public ResponseEntity<IndexingResponse> getStopIndexing() throws InterruptedException {
        if(indexingState){
            stopIndexing();
            indexingState = false;
            return new ResponseEntity<>(creatResponse(false, !indexingState), HttpStatus.OK);
        }
        else {
            return new ResponseEntity<>(creatResponse(false, indexingState), HttpStatus.BAD_REQUEST);
        }


    }

    private void startIndexing() {
        listIndexingServiseImpl = new ArrayList<>();
        tpe = Executors.newFixedThreadPool(sitesListFromProperties.getSites().size());
        int countSite=1;
        SubClassIndexingServise.recreating.set(true);
        for(Site site: sitesList.getSites()){
            iis = new SubClassIndexingServise();
            iis.init(site, sitesListFromProperties, uSerAgent, siteRepository, pageReposytory, countSite);
            listIndexingServiseImpl.add(iis);
            countSite++;
          }
        for(SubClassIndexingServise scis: listIndexingServiseImpl){
                tpe.execute(()->scis.startIndexing());

        }

    }
    private void stopIndexing() throws InterruptedException {
        for(SubClassIndexingServise iis: listIndexingServiseImpl){
            iis.stopIndexing();
        }

        tpe.shutdownNow();
    }
    public class SubClassIndexingServise {
        private  int numberSiteApplicationProperties;

        private SitesList sitesListFromProperties;
        private USerAgent userAgent;
        private SiteRepository siteRepository;
        private PageReposytory pageReposytory;
        private ForkJoinPool fjp;
        public static AtomicBoolean recreating = new AtomicBoolean();

        private long siteId;
        private Site site;

        private String marker;
        private Set notEyetParsingLinkWhereStopedUser;
        private ParserForkJoin task;

    public void init(Site site, SitesList sitesList, USerAgent userAgent, SiteRepository siteRepository, PageReposytory pageReposytory, int numberSiteApplicationProperties){
        this.site = site;
        this.sitesListFromProperties = sitesList;
        this.userAgent = userAgent;
        this.siteRepository = siteRepository;
        this.pageReposytory=pageReposytory;
        this.siteId = numberSiteApplicationProperties;
    }

    public synchronized boolean startIndexing(){
         recreatingTableSiteAndTable();
         parsing(site.getUrl(), site);
         return true;
    }

    public boolean stopIndexing() throws InterruptedException {
        task.blockWorkForkJoin=true;
        task.stopIndexing();
        fjp.shutdownNow();
        return true;

    }
    private boolean parsing(String url, Site site) {
        searchengine.model.Site siteInBAse = returnModelSite(siteId, StatusEnum.INDEXING, getDataTime(), "", url, site.getName());
        synchronized (siteRepository){
            siteRepository.save(siteInBAse);
        }
        marker = url;
        notEyetParsingLinkWhereStopedUser = new HashSet();
        task = new ParserForkJoin(url, siteInBAse, marker, notEyetParsingLinkWhereStopedUser);
        task.blockWorkForkJoin=(false);
        long Start = System.currentTimeMillis();
        fjp.invoke(task);
        siteInBAse.setStatus(StatusEnum.INDEXED);
        siteRepository.save(siteInBAse);
        return true;
    }
    private void recreatingTableSiteAndTable() {
        synchronized (recreating){
            if(recreating.get()){
                recreating.set(false);
                siteRepository.dropTableSiteAndPage();
                siteRepository.createTableSite();
                siteRepository.createTablePage();
                siteRepository.createTableLemma();
                siteRepository.createTableIndex();
                fjp=new ForkJoinPool();
            }
            else{
                fjp=new ForkJoinPool();
            }
        }

    }

    private searchengine.model.Site returnModelSite(long id, StatusEnum indexing, String dataTime, String error, String url, String nameSite) {
        searchengine.model.Site site = new searchengine.model.Site();
        site.setId(id);
        site.setStatus(indexing);
        site.setStatusTime(dataTime);
        site.setLastError(error);
        site.setUrl(url);
        site.setName(nameSite);
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
        int statusResponse = -1;
        if ((!validUrl(url))||(!contaning(url, marker))|| errorLinks.contains(url)) {
            notEyetParsingLinkWhereStopedUser.remove(url);
            return setUrlinPage;
        }
        Document document = null;
        try {
            document = Jsoup.connect(url)
                    .userAgent(userAgent.getUserAgent())
                    .referrer(userAgent.getReferrer())
                    .get();
            statusResponse = document.connection().response().statusCode();

        } catch (IOException e) {
            synchronized (url){
                sendErrorBase("Error pars url " + url + "  " +e.toString());
                errorLinks.add(url);
            }
        }
        if (document == (null)) {
            notEyetParsingLinkWhereStopedUser.remove(url);
            return setUrlinPage;
        }
        synchronized (pageReposytory){
         if (dontContained((url))) {
                pageReposytory.save(new Page((int) (pageReposytory.count() + 1), site, (url),
                        statusResponse, document.head().toString()+document.body().toString()));
                setTime(site);
                startLematization(url);
        }
         else{
             notEyetParsingLinkWhereStopedUser.remove(url);
             return setUrlinPage;
         }
        }
        Elements links = document.select("a[href]");
        Set<String> linkss= links.stream().map(link-> link.attr("abs:href")).collect(Collectors.toSet());
        setUrlinPage = getSetLinkAfterCheckConsistBase(linkss).
                                     stream().filter(link->(contaning(link, marker) && validUrl(link) && !link.isEmpty())).
                                     collect(Collectors.toSet());

      if(!blockWorkForkJoin){
          notEyetParsingLinkWhereStopedUser.addAll(setUrlinPage);
          notEyetParsingLinkWhereStopedUser.remove(url);
      }
        return setUrlinPage;
    }

        private void startLematization(String url) {
            SubLematizatorController.getInstance().addDeque(url);
        }


        public boolean contaning(String absLink, String marker) {
            /*if (absLink == "/www.playback") {
                int y = 0;
            }*/

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
        setStatusSiteINDEXED(site);
        }

        private void setStatusSiteINDEXED(searchengine.model.Site site){
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
            ArrayList<String> response = pageReposytory.getByPath(absLink);
            return response.isEmpty();
        }

        private Set<String> getSetLinkAfterCheckConsistBase(Set<String> inputSet){
            Set<String> linkExistIntoBase = pageReposytory.getListPageonPath(inputSet);
            for(String s: linkExistIntoBase){
                inputSet.remove(s);
            }
            return inputSet;
        }

    }

    }

}


