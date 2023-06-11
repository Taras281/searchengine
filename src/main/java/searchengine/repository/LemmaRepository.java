package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    List<Lemma> findAllByLemmaIn(Set<String> setLems);

    ArrayList<Lemma> findAllBySite(Site idSite);


    int countBySite(Site siteModel);
}
