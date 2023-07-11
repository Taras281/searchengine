package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Set;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {
    Page findByPathAndSite(String path, Site site);
    Set<Page> findAllBySiteAndPathIn(Site site, Set<String> urlSet);
    int countBySite(Site siteModel);
}
