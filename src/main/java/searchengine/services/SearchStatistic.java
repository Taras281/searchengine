package searchengine.services;

import searchengine.dto.search.SearchResponce;

public interface SearchStatistic {
    String query = null;
    String  site = null;
    int  offset = 0;
    int  limit = 0;
    SearchResponce getStatistics();


    void setQuery(String query);
    void setSite(String site);
    void setOffset(int offset);
    void setLimit(int limit);
}
