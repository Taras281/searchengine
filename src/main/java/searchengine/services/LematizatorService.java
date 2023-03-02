package searchengine.services;


import org.springframework.http.ResponseEntity;
import searchengine.dto.responce.IndexingResponse;

public interface LematizatorService{

    ResponseEntity<IndexingResponse> getResponse(String uri);
}
