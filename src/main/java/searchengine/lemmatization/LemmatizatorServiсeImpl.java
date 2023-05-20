package searchengine.lemmatization;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Label;
import searchengine.config.SitesList;
import searchengine.config.UserAgent;
import searchengine.dto.responce.IndexingResponse;
import searchengine.dto.responce.IndexingResponseNotOk;
import searchengine.dto.responce.IndexingResponseOk;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.ExceptionNotUrl;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class LemmatizatorServiсeImpl implements LemmatizatorService {


    private Page page;
    @Autowired
    IndexRepository indexReposytory;

    @Autowired
    LemmaRepository lemmaReposytory;

    @Autowired
    PageRepository pageReposytory;

    @Autowired
    SiteRepository siteReposytory;

    @Autowired
    Lemmatizator lematizator;
    @Autowired
    SitesList siteList;
    @Autowired
    UserAgent userAgent;
    @Autowired
    IndexingResponseNotOk indexingResponseNotOk;
    @Autowired
    IndexingResponseOk indexingResponseOk;
    @Autowired
    Label myLabel;
    @Autowired
    Logger logger;
    @Autowired
    private SitesList sitesList;;

    private boolean rewritePage;

    public void setRewritePage(boolean rewritePage) {
        this.rewritePage = rewritePage;
    }
    public ResponseEntity<IndexingResponse> getResponse(String uri) {
        page = getPageFromUri(uri);
        if(!validUri(uri)){
            indexingResponseNotOk.setResult(false);
            indexingResponseNotOk.setError(myLabel.getCheckPage());
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.OK);
        }
        if (page==null){
            indexingResponseNotOk.setResult(false);
            indexingResponseNotOk.setError(myLabel.getThisPageOutSite());
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.OK);
        }

        if (uriContainsSiteList(uri)) {
            this.setRewritePage(true);
            this.runing();
            indexingResponseOk.setResult(true);
            return new ResponseEntity<>(indexingResponseOk, HttpStatus.OK);}
        else{
            indexingResponseNotOk.setError(myLabel.getThisPageOutSite());
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.BAD_REQUEST);
       }
     }

    private boolean validUri(String uri) {
        return  uri.matches("^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");
    }

    private boolean uriContainsSiteList(String uri) {
        return sitesList.getSites().stream().map(l-> uri.contains(l.getUrl())).anyMatch(b->b==true);
    }

    public void runing() {writeBaseLemsTableIndex(page);
    }

    public void setPathParsingLink(Page page) {
        this.page = page;
    }

    public void writeBaseLemsTableIndex(Page page) {
            Site site = page.getSite();
            if(rewritePage){
                pageReposytory.save(page);
            }
            if (page==null){
                return;
            }
        String content = page.getContent();
        HashMap<String, Integer> lemsFromPage = lematizator.getLems(content);
        // надо получить все леммы и индексы которые есть в базе для текущей страници
        // леммы получить по списку лем со страници
        // при создании индексов сначала создать  для уже существующих лемм, а затем для новых лемм
        // которых нет записать (создать список еще не записанных лемм. и из них слелать список индексов)
        List<Lemma> lemmaFromBase = getLemsFromBase(lemsFromPage);// пересоздаем леммы для БД из слов которые есть на странице
        HashMap<String, Integer> nameLemmaNoYetWriteBase = getLemsNotYetWriteBase(lemsFromPage, lemmaFromBase);
        List<Lemma> lemmaNoYetWriteBase = getListLemmsForSaveBase(nameLemmaNoYetWriteBase, site);
        lemmaFromBase.addAll(lemmaNoYetWriteBase);
        List<Index> indexFromBaseOptionTwoo = getIndex(lemmaFromBase, page, lemsFromPage);
        lemmaReposytory.saveAll(lemmaFromBase);
        indexReposytory.saveAll(indexFromBaseOptionTwoo);
    }



    private Index getIndex(Lemma lemma, Page page, int rank) {
        Index index = new Index();
        index.setRank(rank);
        index.setLemmaId(lemma);
        index.setPageId(page);
        return  index;
    }
    private List<Index> getIndex(List<Lemma> lemmaFromBase, Page page, HashMap<String, Integer> lemsFromPage) {
        List<Index> indexList = new ArrayList<>();
        String content = Jsoup.parse(page.getContent()).text();
        HashMap<String, Integer> lemms = lematizator.getLems(content);
        for(Lemma lemma: lemmaFromBase){
            int rank = lemms.get(lemma.getLemma());
            indexList.add(getIndex(lemma, page, rank));
        }
        return  indexList;
    }


    private List<Lemma> getLemsFromBase(HashMap<String, Integer> lemsFromPage) {
        Set<String> keys=lemsFromPage.keySet();
        List<Lemma> list= lemmaReposytory.findAllByLemmaIn(keys);
        for(Lemma lemma:list){
           lemma.setFrequency(lemma.getFrequency()+1);
        }
        list = deleteExcessLemma(list, keys);
        return  list;
    }

    private List<Lemma> deleteExcessLemma(List<Lemma> list, Set<String> keys) {
        List<Lemma> result = new ArrayList<>();
        result = list.stream().filter(lemma -> keys.contains(lemma.getLemma())).collect(Collectors.toList());
        return result;
    }

    private List<Lemma> getListLemmsForSaveBase(HashMap<String, Integer> nameLemmaNoYetWriteBase, Site site) {
        List<Lemma> result = new ArrayList<>();
        for(Map.Entry<String, Integer> entryLemma: nameLemmaNoYetWriteBase.entrySet()){
            result.add(getLemma(entryLemma, site));
        }
        return result;
    }

    private Lemma getLemma(Map.Entry<String, Integer> entryLemma, Site site) {
        Lemma lemma = new Lemma();
        lemma.setLemma(entryLemma.getKey());
        lemma.setFrequency(1);
        lemma.setSite(site);
        return lemma;
    }

    private HashMap<String, Integer> getLemsNotYetWriteBase(HashMap<String, Integer> lemsFromPage, List<Lemma> lemmaFromBase) {
        Set<String> lemms = lemsFromPage.keySet();
        HashMap<String, Integer> resultLemms = new HashMap<>();
        List<String> lemmsFromBase = lemmaFromBase.stream().map(lemma -> lemma.getLemma()).collect(Collectors.toList());
        for(String lemma: lemms){
            if(!lemmsFromBase.contains(lemma)){
                resultLemms.put(lemma, lemsFromPage.get(lemma));
            }
        }
        return  resultLemms;

    }


    private Page getPageFromUri(String uri) {
        ArrayList<String> listUri = (ArrayList<String>) sitesList.getSites().stream().map(s->s.getUrl()).collect(Collectors.toList());
        Page p=null;
        for(String site: listUri){
            if(uri.contains(site)){
                Site s = siteReposytory.findByUrl(site);
                String urlForPath = null;
                try {
                    urlForPath = remoovePrefix(s, uri);
                } catch (ExceptionNotUrl e) {
                    logger.error("LematizatorServiseImpl " + e);
                }
                p = pageReposytory.findByPathAndSite(urlForPath, s);
                if(p!=null){
                    pageReposytory.delete(p);
                }
                Document document = getDocument(uri);
                int statusResponse = document.connection().response().statusCode();
                p =  new Page(1, s, urlForPath,
                        statusResponse, document.head().toString()+document.body().toString());
                p=pageReposytory.save(p);
            }
        }
        return p;

    }


    private String remoovePrefix(searchengine.model.Site site, String url) throws ExceptionNotUrl {
        if (!url.contains(site.getUrl())) {
            throw new ExceptionNotUrl();
        }
        String result=url.replaceAll(site.getUrl(),"");
        return result;
    }

    private Document getDocument(String url) {
        Document document = null;
        try {
            document = Jsoup.connect(url)
                    .userAgent(userAgent.getUserAgent())
                    .referrer(userAgent.getReferrer())
                    .get();
        } catch (IOException e) {
            synchronized (url){
                String error = "Error pars url " + url + "  " +e.toString();
            }
        }
        return document;
    }

}
