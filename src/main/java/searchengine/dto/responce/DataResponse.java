package searchengine.dto.responce;

import lombok.Data;
import searchengine.dto.search.DetailedStatisticsSearch;


@Data
public class DataResponse {
    boolean result;
    int count;
    DetailedStatisticsSearch[] detailedStatisticsSearchList;
}
