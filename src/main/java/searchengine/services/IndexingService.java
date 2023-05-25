package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.responce.IndexingResponse;

public interface IndexingService {
    ResponseEntity getStartResponse();
    ResponseEntity getStopIndexing() throws InterruptedException;
    ResponseEntity<IndexingResponse> getResponseIndexing(String uri);

}
