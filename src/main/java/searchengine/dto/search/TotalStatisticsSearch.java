package searchengine.dto.search;

import lombok.Data;

@Data
public class TotalStatisticsSearch {
    private int count;
    private DetailedStatisticsSearch[] data;
}


