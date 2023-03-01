package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.responce.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    SearchStatistic searchStatistic;
    @Autowired
    IndexingService indexingServise;
    private final LematizatorService lematizatorServise;
    private final StatisticsService statisticsService;

    private final IndexingResponseNotOk indexingResponseNotOk;

    public ApiController(StatisticsService statisticsService,
                         IndexingResponseNotOk indexingResponseNotOk, LemmatizatorServiсeImpl lematizatorServiсe) {
        this.statisticsService = statisticsService;
        this.indexingResponseNotOk = indexingResponseNotOk;
        this.lematizatorServise = lematizatorServiсe;

    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }
    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> indexing() {
     return indexingServise.getStartResponse();
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() throws InterruptedException {
        return indexingServise.getStopIndexing();
    }

    @GetMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexingPage(@RequestParam("url") String uri){
        return lematizatorServise.getResponse(uri);
    }

    @PostMapping(value = "/indexPage", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<IndexingResponse> indexingPagePost(UriForPost url){
        String u = url.getUrl().replaceAll("%3A%2F%2F", "://");
        return lematizatorServise.getResponse(u);
    }

    @GetMapping("/search")
    public ResponseEntity<IndexingResponse> search(@RequestParam(value = "query", required = false) String  query,
                                                   @RequestParam(value = "site", required = false, defaultValue = "-1") String  site,
                                                   @RequestParam(value = "offset", required = false, defaultValue = "0") String  offset,
                                                   @RequestParam(value = "limit", required = false, defaultValue = "10000") String  limit){
        query.trim();
        searchStatistic.setLimit(Integer.parseInt(limit));
        searchStatistic.setSite(site);
        searchStatistic.setQuery(query);
        searchStatistic.setOffset(Integer.parseInt(offset));
        return  searchStatistic.getStatistics(query, site, limit, offset);
    }


}

