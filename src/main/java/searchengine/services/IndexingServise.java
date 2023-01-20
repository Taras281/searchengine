package searchengine.services;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.io.IOException;

public interface IndexingServise {
    void startIndexing();
    void stopIndexing();
}
