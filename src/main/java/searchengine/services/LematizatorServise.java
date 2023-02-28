package searchengine.services;


import org.springframework.http.ResponseEntity;
import searchengine.dto.responce.IndexingResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.HashMap;
import java.util.List;

public interface LematizatorServise extends Runnable {

     public void setPathParsingLink(String pathParsingLink);

     void writeBaseLemsTableIndex(String pathToBase);

     List<Lemma> writeBaseLemsToTableLemma(Page page, Site site, HashMap<String, Integer> counterLems);

     void setRewritePage(boolean rewritePage);

    ResponseEntity<IndexingResponse> getResponse(String uri);
}
