package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.ParserForkJoin;
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

    private ForkJoinPool forkJoinPool;
    private ParserForkJoin parserForkJoin;
    private ArrayList<ParserForkJoin> listTask;


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
        if(siteRepository.findByStatus(StatusEnum.INDEXING).size()<1){
            startIndexing();
            return new ResponseEntity<>(createIndexingResponseOk(), HttpStatus.OK);
        }
        return  new ResponseEntity<>(createResponseNotOk(myLabel.getIndexingStarted()), HttpStatus.OK);
    }

    public ResponseEntity<IndexingResponse> getStopIndexing() throws InterruptedException {
        if(siteRepository.findByStatus(StatusEnum.INDEXING).size()>0){
            try{
                stopIndexing();
            }
            catch (ConcurrentModificationException cme){
                return new ResponseEntity<>(createResponseErrorBase(), HttpStatus.OK);
            }
            return new ResponseEntity<>(createIndexingResponseOk(), HttpStatus.OK);
        }
        else {
            return new ResponseEntity<>(createResponseNotOk(myLabel.getIndexingNotStarted()), HttpStatus.OK);
        }
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
            searchengine.model.Site siteInBase = returnModelSite(StatusEnum.INDEXING, getDataTime(), "", url, site.getName());
            searchengine.model.Site siteFromBase=null;
            Set<String> errorLinks = new HashSet<>();
            CopyOnWriteArraySet<String> notEyetParsingLinkWhereStopedUser = new  CopyOnWriteArraySet<>();
            synchronized (siteRepository) {
                siteFromBase = siteRepository.save(siteInBase);
            }
            parserForkJoin =  new ParserForkJoin(pageReposytory, siteRepository, logger, subLemmatizatorController, myLabel, userAgent,
                                                 notEyetParsingLinkWhereStopedUser, url, url, siteFromBase, errorLinks);
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
    }

