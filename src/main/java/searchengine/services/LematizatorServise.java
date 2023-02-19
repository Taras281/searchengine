package searchengine.services;


import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.HashMap;
import java.util.List;

public interface LematizatorServise extends Runnable {

     public void setPathParsingLink(String pathParsingLink);

     void writeBaseLemsTableIndex(String pathToBase);

     List<Lemma> writeBaseLemsToTableLemma(Page page, Site site, HashMap<String, Integer> counterLems);

     void setRewritePage(boolean rewritePage);
}
