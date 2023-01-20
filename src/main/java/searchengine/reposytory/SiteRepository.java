package searchengine.reposytory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;

import javax.persistence.Entity;
import java.util.ArrayList;
@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {

    @Modifying
    @Transactional
    @Query(value = "CREATE TABLE `page` (" +
            "`id` int NOT NULL AUTO_INCREMENT," +
            "`code` int NOT NULL," +
            "`content` mediumtext COLLATE cp1251_bin NOT NULL," +
            "`path` text COLLATE cp1251_bin NOT NULL," +
            "`site_id` int DEFAULT NULL," +
            "`thread` TEXT DEFAULT NULL," +
            "PRIMARY KEY (`id`), " +
            "KEY `index_path` (`path`(50)), " +
            "KEY `FKj2jx0gqa4h7wg8ls0k3y221h2` (`site_id`), " +
            "CONSTRAINT `FKj2jx0gqa4h7wg8ls0k3y221h2` FOREIGN KEY (`site_id`) REFERENCES `site` (`id`) ON DELETE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=cp1251 COLLATE=cp1251_bin;",
            nativeQuery = true)
    void createTablePage();
    @Modifying
    @Transactional
    @Query(value = "CREATE TABLE `site` (" +
            "`id` int NOT NULL AUTO_INCREMENT," +
            "`last_error` text COLLATE cp1251_bin," +
            "`name` varchar(255) COLLATE cp1251_bin NOT NULL," +
            "`status` enum('INDEXING','INDEXED','FAILED') COLLATE cp1251_bin NOT NULL," +
            "`status_time` datetime NOT NULL," +
            "`url` varchar(255) COLLATE cp1251_bin NOT NULL, " +
            "PRIMARY KEY (`id`)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=cp1251 COLLATE=cp1251_bin;",
            nativeQuery = true)
    void createTableSite();

    @Modifying
    @Transactional
    @Query(value = "drop table search_engine.page, search_engine.site;",
            nativeQuery = true)
    void dropTableSiteAndPage();



}
