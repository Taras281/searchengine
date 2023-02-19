package searchengine.services;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.io.IOException;
import java.util.concurrent.Callable;

public interface IndexingServise {
    boolean startIndexing();
    boolean stopIndexing() throws InterruptedException;

}
