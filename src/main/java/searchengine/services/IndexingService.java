package searchengine.services;

import org.jsoup.nodes.Document;
import org.springframework.http.ResponseEntity;
import searchengine.config.StatusEnum;
import searchengine.dto.responce.IndexingResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.util.List;

public interface IndexingService {
    PageRepository pageRepository = null;
    SiteRepository siteRepository = null;
    ResponseEntity getStartResponse();
    ResponseEntity getStopIndexing() throws InterruptedException;
    ResponseEntity<IndexingResponse> getResponseIndexing(String uri);

    void addBase(Page newPage);

    List<Page> getPages();

    void saveError(Site site, String s);
    StatusEnum getStatus(Site site);

    Document getDocument(Site site, String urlPage);

    String remoovePrefix(Site site, String l);

    boolean validUrl(String concat);
}
