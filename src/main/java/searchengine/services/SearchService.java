package searchengine.services;

import org.springframework.http.ResponseEntity;

public interface SearchService {
    ResponseEntity getStatistics(String query, String site, String limit, String offset);
}
