package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.tools.ParserForkJoin;
import searchengine.config.*;
import searchengine.dto.responce.IndexingResponse;
import searchengine.dto.responce.IndexingResponseNotOk;
import searchengine.dto.responce.IndexingResponseOk;
import searchengine.tools.ExceptionNotUrl;
import searchengine.tools.ParserForkJoinAction;
import searchengine.tools.lemmatization.Lemmatizator;
import searchengine.model.Page;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final SiteRepository siteRepository;

    private final PageRepository pageRepository;

    private UserAgent userAgent;

    private SitesList sitesList;

    private Logger logger;
    private ForkJoinPool forkJoinPool;
    private ParserForkJoinAction parserForkJoinAction;
    private ParserForkJoin parserForkJoin;
    //private ArrayList<ParserForkJoin> listTask;
    private ArrayList<ParserForkJoinAction> listTask;
    private Lemmatizator lemmatizatorServiсe;
    private Label myLabel;
    private Set<Long> siteIdListForCheckStopped;
    public IndexingServiceImpl(SiteRepository siteRepository, PageRepository pageReposytory,
                               UserAgent userAgent, SitesList sitesList, Label label, Logger logger,
                               Lemmatizator lemmatizatorServiсe) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageReposytory;
        this.sitesList = sitesList;
        this.myLabel = label;
        this.userAgent = userAgent;
        this.logger = logger;
        this.lemmatizatorServiсe = lemmatizatorServiсe;
    }

    public ResponseEntity<IndexingResponse> getStartResponse(){
        if(siteRepository.findByStatus(StatusEnum.INDEXING).size()<1){
            siteIdListForCheckStopped = new HashSet<>();
            startIndexing();
            return new ResponseEntity<>(createIndexingResponseOk(), HttpStatus.OK);
        }
        return  new ResponseEntity<>(createResponseNotOk(myLabel.getIndexingStarted()), HttpStatus.OK);
    }

    public ResponseEntity<IndexingResponse> getStopIndexing() throws InterruptedException {
        if(siteRepository.findByStatus(StatusEnum.INDEXING).size()>0){
            synchronized (siteRepository){
                ArrayList<searchengine.model.Site> sites = siteRepository.findByStatus(StatusEnum.INDEXING);
                for(searchengine.model.Site site: sites){
                    site.setStatus(StatusEnum.FAILED);
                    site.setLastError("Индексация остановлена пользователем");
                    siteIdListForCheckStopped.add(site.getId());
                }
                siteRepository.saveAll(sites);
            }
            return new ResponseEntity<>(createIndexingResponseOk(), HttpStatus.OK);
        }
        else {
            return new ResponseEntity<>(createResponseNotOk(myLabel.getIndexingNotStarted()), HttpStatus.OK);
        }
    }

    public ResponseEntity<IndexingResponse> getResponseIndexing(String uri) {
        Page page = getPageFromUri(uri);
        IndexingResponseNotOk indexingResponseNotOk = new IndexingResponseNotOk();
        IndexingResponseOk indexingResponseOk = new IndexingResponseOk();
        if(!validUrl(uri)){
            indexingResponseNotOk.setResult(false);
            indexingResponseNotOk.setError(myLabel.getCheckPage());
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.OK);
        }
        if (page==null){
            indexingResponseNotOk.setResult(false);
            indexingResponseNotOk.setError(myLabel.getThisPageOutSite());
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.OK);
        }

        if (uriContainsSiteList(uri)) {
            /*lemmatizatorServiсe.setRewritePage(true);
            lemmatizatorServiсe.setPathParsingLink(page);
            lemmatizatorServiсe.runing();
            indexingResponseOk.setResult(true);*/
            return new ResponseEntity<>(indexingResponseOk, HttpStatus.OK);}
        else{
            indexingResponseNotOk.setError(myLabel.getThisPageOutSite());
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public void addBase(Page newPage) {
        synchronized (pageRepository){
        if(siteIdListForCheckStopped.contains(newPage.getSite().getId())){
           newPage.getSite().setStatus(StatusEnum.FAILED);
        }
            pageRepository.save(newPage);
            searchengine.model.Site site = newPage.getSite();
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    @Override
    public List<Page> getPages() {
        return pageRepository.findAll();
    }
    @Override
    public void saveError(searchengine.model.Site site, String s) {
        synchronized (siteRepository){
        site.setLastError(s);
        site.setStatusTime(LocalDateTime.now());
               siteRepository.save(site);
    }}

    @Override
    public StatusEnum getStatus(searchengine.model.Site site) {
        return siteRepository.findByUrl(site.getUrl()).getStatus();
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
            searchengine.model.Site siteForBase = returnModelSite(StatusEnum.INDEXING, getDataTime(), "", url, site.getName());
            searchengine.model.Site siteFromBase=null;
            synchronized (siteRepository) {
                siteFromBase = siteRepository.save(siteForBase);
            }
            parserForkJoinAction =  new ParserForkJoinAction(siteFromBase,"",this, new HashSet<>(), userAgent);
            listTask.add(parserForkJoinAction);
        }
        for (ParserForkJoinAction pfj: listTask){
            new Thread(()->{
                forkJoinPool.invoke(pfj);
                if(!siteIdListForCheckStopped.contains(pfj.getSite().getId()))
                {pfj.getSite().setStatus(StatusEnum.INDEXED);}
                siteRepository.save(pfj.getSite());
            }).start();
        }
    }

    private searchengine.model.Site returnModelSite(StatusEnum indexing, String dataTime, String error, String url, String nameSite) {
        searchengine.model.Site site=siteRepository.findByUrl(url);
        if(site==null){
            site = new searchengine.model.Site();
            site.setStatus(indexing);
            site.setStatusTime(LocalDateTime.now());
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
    public boolean validUrl(String uri) {
        return  uri.matches("^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");
    }
    private Page getPageFromUri(String uri) {
        ArrayList<String> listUri = (ArrayList<String>) sitesList.getSites().stream().map(s->s.getUrl()).collect(Collectors.toList());
        Page p=null;
        searchengine.model.Site s;
        for(String site: listUri){
            if(uri.contains(site)){
                s = siteRepository.findByUrl(site);
                String urlForPath = null;
                urlForPath = remoovePrefix(s, uri);
                p = pageRepository.findByPathAndSite(urlForPath, s);
                if(p!=null){
                    pageRepository.delete(p);
                }
                Document document = getDocument(s, urlForPath);
                int statusResponse = document.connection().response().statusCode();
                p =  new Page(1, s, urlForPath, statusResponse, document.html());
                p = pageRepository.save(p);

            }
        }
        return p;
    }
    private boolean uriContainsSiteList(String uri) {
        return sitesList.getSites().stream().map(l-> uri.contains(l.getUrl())).anyMatch(b->b==true);
    }
    public String remoovePrefix(searchengine.model.Site site, String url){
        String result=url.replaceAll(site.getUrl(),"");
        return result;
    }
    public Document getDocument(searchengine.model.Site site, String urlPage) {
        Document document = null;
        try {
            document = Jsoup.connect(site.getUrl().concat(urlPage))
                    .userAgent(userAgent.getUserAgent())
                    .referrer(userAgent.getReferrer())
                    .get();
        } catch (IOException e) {
            synchronized (urlPage){
                addBase(new Page(1, site, urlPage, 418,""));
                saveError(site, "Error pars url " + urlPage + "  " +e.toString());
            }
        }
        return document;
    }


}

