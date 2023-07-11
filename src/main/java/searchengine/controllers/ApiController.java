package searchengine.controllers;

import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.response.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;
import searchengine.services.SearchService;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class ApiController {
    private SearchService searchStatistic;
    private IndexingService indexingServise;
    private StatisticsService statisticsService;

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
        ResponseEntity<IndexingResponse> resp =indexingServise.getStopIndexing();
        return resp;
    }

    @PostMapping(value = "/indexPage", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<IndexingResponse> indexingPagePost(String url){
        return indexingServise.getResponseIndexing(url);
    }
    @GetMapping("/search")
    public ResponseEntity<IndexingResponse> search(@RequestParam(value = "query", required = false) String  query,
                                                   @RequestParam(value = "site", required = false, defaultValue = "-101") String  site,
                                                   @RequestParam(value = "offset", required = false, defaultValue = "0") String  offset,
                                                   @RequestParam(value = "limit", required = false, defaultValue = "20") String  limit){
        query.trim();
    return  searchStatistic.getStatistics(query, site, limit, offset);
    }


}

