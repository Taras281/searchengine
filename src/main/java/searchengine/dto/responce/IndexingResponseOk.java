package searchengine.dto.responce;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
@JsonSerialize
public class IndexingResponseOk implements IndexingResponse {
    @JsonProperty("result")
    private boolean result;
}

