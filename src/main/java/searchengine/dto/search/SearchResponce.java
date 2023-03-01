package searchengine.dto.search;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import searchengine.dto.search.DetailedStatisticsSearch;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponce {
    boolean result;
    int count;
    DetailedStatisticsSearch[] data;
    String error;
}
