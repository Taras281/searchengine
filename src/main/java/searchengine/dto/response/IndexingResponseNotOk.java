package searchengine.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
@JsonSerialize
public class IndexingResponseNotOk implements IndexingResponse {
    @JsonProperty("result")
    private boolean result;
    @JsonProperty("error")
    private String error;
}
