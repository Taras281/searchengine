package searchengine.reposytory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.ArrayList;
import java.util.List;

public interface IndexReposytory extends JpaRepository<Index, Integer> {

    @Modifying
    @Transactional
    @Query(value = "select * from `index` where lemma_id in :listIdLemm",
            nativeQuery = true)
    ArrayList<Index> getAllContainsLems(List<Integer> listIdLemm);

    List<Index> findAllByPageId(Page page);

    List<Index> findAllByLemmaId(Lemma lemma);
    @Modifying
    @Transactional
    @Query(value = "select * from `index` where page_id in :listPageId",
            nativeQuery = true)
    List<Index> getAllByPageId(List<Long> listPageId);

}
