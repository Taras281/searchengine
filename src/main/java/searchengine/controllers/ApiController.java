package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingServise;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired
    IndexingServise indexingServise;

    private final StatisticsService statisticsService;

    public ApiController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startindexing")
    public String indexing()  {
        indexingServise.startIndexing();
        return "";
    }

    @GetMapping("/stopindexing")
    public String stopIndexing()  {
        indexingServise.stopIndexing();
        return "";
    }
}
