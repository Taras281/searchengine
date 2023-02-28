package searchengine.dto.responce;

import lombok.Data;
import org.springframework.stereotype.Service;

@Data
@Service
public class IndexingResponseNotOk implements IndexingResponse {
    private boolean result;
    private String error;
}
