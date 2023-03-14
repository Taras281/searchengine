package searchengine.wordsearchresponse;

import org.springframework.http.ResponseEntity;

public interface SearchStatistic {
    ResponseEntity getStatistics(String query, String site, String limit, String offset);
}
