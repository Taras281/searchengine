package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;
import java.util.ArrayList;
import java.util.Set;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    Set<Page> findAllByPathIn(Set<String> listPath);

    ArrayList<Page> findAllBySite(Site siteModel);


    Page findByPathAndSite(String path, Site site);

    Set<Page> findAllBySiteAndPathIn(Site site, Set<String> urlSet);
}
