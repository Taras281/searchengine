package searchengine.dto.response;

import lombok.Data;
import searchengine.dto.search.DetailedStatisticsSearch;


@Data
public class DataResponse {
    boolean result;
    int count;
    DetailedStatisticsSearch[] detailedStatisticsSearchList;
}
