package searchengine.reposytory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.ArrayList;

@Repository
public interface PageReposytory extends JpaRepository<Page, Long> {
    @Modifying
    @Transactional
    @Query(value = "truncate table search_engine.page",
            nativeQuery = true)
    void truncateMyTable();

    @Modifying
    @Transactional
    @Query(value = "Select path from search_engine.page where path=:absLink",
            nativeQuery = true)
    ArrayList<String> getByPath(String absLink);
}
