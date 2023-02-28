package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.SitesList;
import searchengine.config.USerAgent;
import searchengine.dto.responce.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.reposytory.PageReposytory;
import searchengine.reposytory.SiteRepository;
import searchengine.services.*;

import java.util.List;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    SiteRepository siteRepository;
    @Autowired
    PageReposytory pageReposytory;
    @Autowired
    SitesList sitesListFromProperties;
    @Autowired
    USerAgent uSerAgent;

    @Autowired
    SitesList sitesList;

    @Autowired
    SearchStatistic searchStatistic;

    @Autowired
    IndexingServiseImpl indexingServiseImpl;


    private final LematizatorServise lematizatorServise;
    private final StatisticsService statisticsService;
    private final IndexingResponseOk indexingResponseOk;

    private final IndexingResponseNotOk indexingResponseNotOk;
    private boolean startIndexing;
    private int numberSiteforParsing=3;
    private List<IndexingServiseImpl> listIndexingServiseImpl;
    ExecutorService tpe;

    {
        startIndexing=false;
    }

    public ApiController(StatisticsService statisticsService, IndexingResponseOk indexingResponse,
                         IndexingResponseNotOk indexingResponseNotOk, LemmatizatorServiseImplTreeple lematizatorServise) {
        this.statisticsService = statisticsService;
        this.indexingResponseOk = indexingResponse;
        this.indexingResponseNotOk = indexingResponseNotOk;
        this.lematizatorServise = lematizatorServise;

    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }
    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> indexing() {
        /*if(!startIndexing){
        startIndexing=true;
        listIndexingServiseImpl = new ArrayList<>();
        tpe = Executors.newFixedThreadPool(numberSiteforParsing);
        int countSite=1;
        IndexingServiseImpl.recreating.set(true);
        for(Site site: sitesList.getSites()){
            IndexingServiseImpl iis = new IndexingServiseImpl();
            iis.init(site, sitesListFromProperties, uSerAgent, siteRepository, pageReposytory, countSite);
            listIndexingServiseImpl.add(iis);
            countSite++;
            tpe.submit(()->iis.startIndexing());

        }
        indexingResponseOk.setResult(true);
        return  new ResponseEntity<>(indexingResponseOk, HttpStatus.OK);
        }
        else {
            indexingResponseNotOk.setResult(false);
            indexingResponseNotOk.setError("Индексация уже запущена");
            return  new ResponseEntity<>(indexingResponseNotOk, HttpStatus.OK);
        }*/
     return indexingServiseImpl.getStartResponse();

    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() throws InterruptedException {
        /*if (startIndexing){
            startIndexing=false;
            indexingResponseOk.setResult(true);
            for(IndexingServiseImpl iis: listIndexingServiseImpl){
                //iis.stopIndexing();
            }
            tpe.shutdownNow();
            return new ResponseEntity<>(indexingResponseOk, HttpStatus.OK);
        }
        else{
            indexingResponseNotOk.setResult(false);
            indexingResponseNotOk.setError("индексация не запущена");
            return  new ResponseEntity<>(indexingResponseNotOk, HttpStatus.OK);
        }*/
        return indexingServiseImpl.getStopIndexing();
    }

    @GetMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexingPage(@RequestParam("url") String uri){
        return lematizatorServise.getResponse(uri);
        /*if (uriContainsSiteList(uri))
        {lematizatorServise.setPathParsingLink(uri);
         lematizatorServise.setRewritePage(true);
        new Thread(lematizatorServise).start();
        indexingResponseOk.setResult(true);
        return new ResponseEntity<>(indexingResponseOk, HttpStatus.OK);}
        else{
            indexingResponseNotOk.setError("Данная страница находится за пределами сайтов,\n" +
                                             "указанных в конфигурационном файле");
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.BAD_REQUEST);
        }*/
    }

    @PostMapping(value = "/indexPage", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<IndexingResponse> indexingPagePost(UriForPost url){
        String u = url.getUrl().replaceAll("%3A%2F%2F", "://");
        return lematizatorServise.getResponse(u);
        /*if (uriContainsSiteList(uri))
        {lematizatorServise.setPathParsingLink(uri);
         lematizatorServise.setRewritePage(true);
        new Thread(lematizatorServise).start();
        indexingResponseOk.setResult(true);
        return new ResponseEntity<>(indexingResponseOk, HttpStatus.OK);}
        else{
            indexingResponseNotOk.setError("Данная страница находится за пределами сайтов,\n" +
                                             "указанных в конфигурационном файле");
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.BAD_REQUEST);
        }*/
    }



    @GetMapping("/search")
    public ResponseEntity<IndexingResponse> search(@RequestParam(value = "query", required = false) String  query,
                                                   @RequestParam(value = "site", required = false, defaultValue = "-1") String  site,
                                                   @RequestParam(value = "offset", required = false, defaultValue = "0") String  offset,
                                                   @RequestParam(value = "limit", required = false, defaultValue = "10000") String  limit){

        if(query==null){
            indexingResponseNotOk.setError("Задан пустой поисковый запрос");
            indexingResponseNotOk.setResult(false);
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.BAD_REQUEST);
        }
        searchStatistic.setLimit(Integer.parseInt(limit));
        searchStatistic.setSite(site);
        searchStatistic.setQuery(query);
        searchStatistic.setOffset(Integer.parseInt(offset));
        SearchResponce responce = searchStatistic.getStatistics();
        return  new ResponseEntity(responce, HttpStatus.OK);
    }


}

