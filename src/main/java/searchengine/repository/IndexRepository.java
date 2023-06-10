package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.ArrayList;
import java.util.List;

public interface IndexRepository extends JpaRepository<Index, Integer> {
    List<Index> findAllByPageId(Page page);
    List<Index> findAllByLemmaId(Lemma lemma);
    List<Index> findAllByPageIdIn(List<Page> listPageId);
}
