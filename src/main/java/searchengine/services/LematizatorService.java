package searchengine.services;


import org.springframework.http.ResponseEntity;
import searchengine.dto.responce.IndexingResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.HashMap;
import java.util.List;

public interface LematizatorService{

    ResponseEntity<IndexingResponse> getResponse(String uri);
}
