package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.responce.IndexingResponse;
import searchengine.model.Page;

public interface IndexingService {
    ResponseEntity getStartResponse();
    ResponseEntity getStopResponse() throws InterruptedException;
    ResponseEntity<IndexingResponse> getResponseIndexing(String uri);
    void addBase(Page listPages);
}
