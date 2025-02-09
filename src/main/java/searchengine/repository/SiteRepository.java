package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;
import searchengine.config.StatusEnum;
import java.util.ArrayList;


@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {
    Site findByUrl(String url);
    ArrayList<Site> findByName(String url);
    ArrayList<Site>  findByStatus(StatusEnum indexing);
}
