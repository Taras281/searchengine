package searchengine.services.lemmatization;


import org.springframework.http.ResponseEntity;
import searchengine.dto.responce.IndexingResponse;

public interface LemmatizatorService {

    ResponseEntity<IndexingResponse> getResponse(String uri);
}
