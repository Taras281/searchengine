package searchengine.dto.responce;

import lombok.Data;
import org.springframework.stereotype.Service;

@Data
@Service
public class IndexingResponseOk implements IndexingResponse {
    private boolean result;
}

