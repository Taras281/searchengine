package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;


@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {

    @Modifying
    @Transactional
    @Query(value = "CREATE TABLE `page` (" +
            "`id` int NOT NULL AUTO_INCREMENT," +
            "`code` int NOT NULL," +
            "`content` mediumtext  NOT NULL," +
            "`path` text  NOT NULL," +
            "`site_id` int DEFAULT NULL," +
            "PRIMARY KEY (`id`), " +
            "UNIQUE KEY `path` (`path`(300)), " +
            "KEY `FKj2jx0gqa4h7wg8ls0k3y221h2` (`site_id`), " +
            "CONSTRAINT `FKj2jx0gqa4h7wg8ls0k3y221h2` FOREIGN KEY (`site_id`) REFERENCES `site` (`id`) ON DELETE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;",
            nativeQuery = true)
    void createTablePage();
    @Modifying
    @Transactional
    @Query(value = "CREATE TABLE `site` (" +
            "`id` int NOT NULL AUTO_INCREMENT," +
            "`last_error` TEXT," +
            "`name` varchar(255) NOT NULL," +
            "`status` enum('INDEXING','INDEXED','FAILED') NOT NULL," +
            "`status_time` datetime NOT NULL," +
            "`url` varchar(255) NOT NULL, " +
            "PRIMARY KEY (`id`)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;",
            nativeQuery = true)
    void createTableSite();

    @Modifying
    @Transactional
    @Query(value = "CREATE TABLE `index` (" +
           " `id` int NOT NULL AUTO_INCREMENT," +
           " `rank` float NOT NULL," +
           " `lemma_id` int DEFAULT NULL," +
           " `page_id` int DEFAULT NULL," +
           " PRIMARY KEY (`id`)," +
           " KEY `FKiqgm34dkvjdt7kobg71xlbr33` (`lemma_id`)," +
           " KEY `FK3uxy5s82mxfodai0iafb232cs` (`page_id`)," +
           "CONSTRAINT `FK3uxy5s82mxfodai0iafb232cs` FOREIGN KEY (`page_id`) REFERENCES `page` (`id`) ON DELETE CASCADE," +
           "CONSTRAINT `FKiqgm34dkvjdt7kobg71xlbr33` FOREIGN KEY (`lemma_id`) REFERENCES `lemma` (`id`) ON DELETE CASCADE)"+
           "ENGINE=InnoDB DEFAULT CHARSET=cp1251 COLLATE=cp1251_bin",
            nativeQuery = true)
    void createTableIndex();

    @Modifying
    @Transactional
    @Query(value =  "CREATE TABLE `lemma` (" +
                    "`id` int NOT NULL AUTO_INCREMENT," +
                    "`frequency` int NOT NULL," +
                    "`lemma` varchar(255) COLLATE cp1251_bin NOT NULL," +
                    "`site_id` int DEFAULT NULL," +
                    "PRIMARY KEY (`id`)," +
                    "KEY `FKfbq251d28jauqlxirb1k2cjag` (`site_id`)," +
                    "CONSTRAINT `FKfbq251d28jauqlxirb1k2cjag` FOREIGN KEY (`site_id`) REFERENCES `site` (`id`) ON DELETE CASCADE)" +
                    "ENGINE=InnoDB DEFAULT CHARSET=cp1251 COLLATE=cp1251_bin",
            nativeQuery = true)
    void createTableLemma();


    @Modifying
    @Transactional
    @Query(value = "drop table search_engine.index, search_engine.lemma, search_engine.page, search_engine.site;",
            nativeQuery = true)
    void dropTableSiteAndPage();


    Site findByUrl(String url);
}
