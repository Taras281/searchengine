package searchengine.dto.search;

import lombok.Data;

import java.util.List;

@Data
public class DataResponse {
    boolean result;
    int count;
    DetailedStatisticsSearch[] detailedStatisticsSearchList;
}
