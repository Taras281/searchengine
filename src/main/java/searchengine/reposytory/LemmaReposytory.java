package searchengine.reposytory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
public interface LemmaReposytory extends JpaRepository<Lemma, Integer> {


    @Modifying
    @Transactional
    @Query(value = "Select * from search_engine.lemma where lemma in :setLems",
            nativeQuery = true)
    List<Lemma> getAllContainsLems(Set<String> setLems);

    @Modifying
    @Transactional
    @Query(value = "Select id, site_id, lemma, frequency from search_engine.lemma where site_id = :idSite",
            nativeQuery = true)
    ArrayList<Lemma> getAllLems(Site idSite);

    @Modifying
    @Transactional
    @Query(value = "Select id, site_id, lemma, frequency from search_engine.lemma where lemma = :k",
            nativeQuery = true)
    int findByLema(String k);
}
