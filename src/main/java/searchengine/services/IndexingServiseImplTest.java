package searchengine.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import searchengine.services.IndexingServiseImpl.ParserForkJoin;

class IndexingServiseImplTest {

    private String response="'CREATE TABLE `page` (\n" +
            "  `id` int NOT NULL AUTO_INCREMENT,\n" +
            "  `code` int NOT NULL,\n" +
            "  `content` mediumtext COLLATE cp1251_bin NOT NULL,\n" +
            "  `path` text COLLATE cp1251_bin NOT NULL,\n" +
            "  `site_id` int DEFAULT NULL,\n" +
            "  PRIMARY KEY (`id`),\n" +
            "  KEY `index_path` (`path`(50)),\n" +
            "  KEY `FKj2jx0gqa4h7wg8ls0k3y221h2` (`site_id`),\n" +
            "  CONSTRAINT `FKj2jx0gqa4h7wg8ls0k3y221h2` FOREIGN KEY (`site_id`) REFERENCES `site` (`id`)\n" +
            ") ENGINE=InnoDB AUTO_INCREMENT=51 DEFAULT CHARSET=cp1251 COLLATE=cp1251_bin'";

    public boolean contaning(String absLink, String marker) {
        if(absLink.contains(marker)){
            return true;
        }
        if(absLink.contains("www")){
        String[] separetedLink = absLink.split("www");

        if(separetedLink.length>2){
            String concatLinc=separetedLink[0]+separetedLink[1];
            return concatLinc.contains(marker);
        }
        }
        return false;
    }

    private String parseResponse(String s) {
        if(s.contains("KEY")){
            String substr=s.substring(s.indexOf("CONSTRAINT"));
            substr = substr.substring(0,substr.indexOf("FOREIGN KEY"));
            int start = substr.indexOf('`');
            int stop = substr.lastIndexOf('`');
            substr = substr.substring(start+1,stop);

            return substr;
        }
        return "";
    }

    @Test
    void parseResponse(){
        assertEquals("FKj2jx0gqa4h7wg8ls0k3y221h2", parseResponse(response));
        assertNotEquals("FKj2jx0gqa4h7wg8ls0k3y221h", parseResponse(response));
    }

    @Test
    void validUrl() {
       assertTrue(contaning("https://www.lenta.ru", "https://www.lenta.ru"));
       assertFalse(contaning("https://lenta.ru", "https://www.lenta.ru"));
       assertTrue(contaning("https://www.lenta.ru/", "https://www.lenta.ru"));
       assertTrue(contaning("https://www.lenta.ru/dxcgads", "https://www.lenta.ru"));
       assertFalse(contaning("https://www.lnta.ru/dxcgads", "https://www.lenta.ru"));
       assertFalse(contaning("https://lnta.ru/dxcgads", "https://www.lenta.ru"));
    }


}