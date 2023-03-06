package searchengine.services.wordsearchresponse;

import org.springframework.http.ResponseEntity;

public interface SearchStatistic {
    String query = null;
    String  site = null;
    int  offset = 0;
    int  limit = 0;
    ResponseEntity getStatistics(String query, String site, String limit, String offset);

    void setQuery(String query);
    void setSite(String site);
    void setOffset(int offset);
    void setLimit(int limit);
}
