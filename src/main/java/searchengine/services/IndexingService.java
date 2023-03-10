package searchengine.services;

import org.springframework.http.ResponseEntity;

public interface IndexingService {
    ResponseEntity getStartResponse();
    ResponseEntity getStopIndexing() throws InterruptedException;

}
