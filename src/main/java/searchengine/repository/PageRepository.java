package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {


    Set<Page> findAllByPathIn(Set<String> listPath);

    Page findByPath(String path);

    ArrayList<Page> findAllBySite(Site siteModel);

    List<Page> findAllByIdIn(List<Long> listPageId);
}
