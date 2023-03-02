package searchengine.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponce {
    boolean result;
    int count;
    DetailedStatisticsSearch[] data;
    String error;
}
