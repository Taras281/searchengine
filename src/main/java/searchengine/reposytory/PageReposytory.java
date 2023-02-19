package searchengine.reposytory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.ArrayList;
import java.util.Set;

@Repository
public interface PageReposytory extends JpaRepository<Page, Long> {


    @Modifying
    @Transactional
    @Query(value = "Select path from search_engine.page where path=:absLink",
            nativeQuery = true)
    ArrayList<String> getByPath(String absLink);


    @Modifying
    @Transactional
    @Query(value = "Select path from search_engine.page where path in :listPath",
            nativeQuery = true)
    Set<String> getListPageonPath(Set<String> listPath);

    @Modifying
    @Transactional
    @Query(value = "Select path from search_engine.page where id =:id",
            nativeQuery = true)
    Set<String> getId(Integer id);

    @Modifying
    @Transactional
    @Query(value = "Select path from search_engine.page",
            nativeQuery = true)
    Set<String> getAll();


    Page findByPath(String path);

    @Modifying
    @Transactional
    @Query(value = "Select * from search_engine.page where site_id = :siteModel",
            nativeQuery = true)
    ArrayList<Page> findAllBySiteId(Site siteModel);




}
