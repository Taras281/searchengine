package searchengine.services;

import lombok.Data;
import searchengine.dto.statistics.StatisticsResponse;

public interface StatisticsService {

    StatisticsResponse getStatistics();

}
