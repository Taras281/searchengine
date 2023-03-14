package searchengine.lemmatization;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
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
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class LemmatizatorServiсeImpl implements LemmatizatorService {
    @PersistenceContext
    private EntityManager entityManager;

    private String pathParsingLink;
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
    SitesList sitesList;

    @Autowired
    IndexingResponseNotOk indexingResponseNotOk;

    @Autowired
    IndexingResponseOk indexingResponseOk;

    @Autowired
    Label myLabel;

    @Autowired
    Logger logger;

    private boolean rewritePage;

    public void setRewritePage(boolean rewritePage) {
        this.rewritePage = rewritePage;
    }
    public ResponseEntity<IndexingResponse> getResponse(String uri) {
        if(!validUri(uri)){
            indexingResponseNotOk.setError(myLabel.getThisPageOutSite());
            indexingResponseNotOk.setError("check url");
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.NOT_FOUND);
        }
        if (uriContainsSiteList(uri))
        {   this.setPathParsingLink(uri);
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



    public void runing() {
        writeBaseLemsTableIndex(pathParsingLink);
    }

    public void setPathParsingLink(String pathParsingLink) {
        this.pathParsingLink = pathParsingLink;
    }

    public void writeBaseLemsTableIndex(String pathToBase) {
            Page page = pageReposytory.findByPath(pathToBase);
            Site site = getSite(pathToBase);
            pageReposytory.flush();
            siteReposytory.flush();
            if(rewritePage){
                page = rewritePage(page, site, pathToBase);
            }
            if (page==null){
                return;
            }
        String content = page.getContent();
        HashMap<String, Integer> lemsFromPage = lematizator.getLems(content);
        // надо получить все леммы и индексы которые есть в базе для текущей страници
        // леммы получить по списку лем со страници
        // при создании индексов сначала создать для уже существующих лемм, а затем для новых лемм
        // которых нет записать (создать список еще не записанных лемм. и из них слелать список индексов)
        List<Lemma> lemmaFromBase = getLemmsOptionTwo(lemsFromPage);
        HashMap<String, Integer> nameLemmaNoYetWriteBase = getLemsNotYetWriteBase(lemsFromPage, lemmaFromBase);
        List<Lemma> lemmaNoYetWriteBase = getListLemmsForSaveBase(nameLemmaNoYetWriteBase, site, lemmaFromBase);
        lemmaFromBase.addAll(lemmaNoYetWriteBase);
        List<Index> indexFromBaseOptionTwoo = getIndex(lemmaFromBase, page);
        entityManager.flush();
        lemmaReposytory.saveAll(lemmaFromBase);
        indexReposytory.saveAll(indexFromBaseOptionTwoo);
    }

    private void delete(Page page) {
        pageReposytory.delete(page);
        pageReposytory.flush();
 }
    private Page rewritePage(Page page, Site site, String pathToBase) {
        if(page!=null){
            delete(page);}
        if(site==null){
            return null;
        }
        String[] resPars=getCodeAndContent(pathToBase);
        pageReposytory.save(getPage(pathToBase, site, resPars));
        page = pageReposytory.findByPath(pathToBase);
        return page;
    }

    private Index getIndex(Lemma lemma, Page page) {
        Index index = new Index();
        index.setRank(lemma.getFrequency());
        index.setLemmaId(lemma);
        index.setPageId(page);
        return  index;
    }
    private List<Index> getIndex(List<Lemma> lemmaFromBase, Page page) {

        List<Index> indexList = new ArrayList<>();
        for(Lemma lemma: lemmaFromBase){
            indexList.add(getIndex(lemma, page));
        }
        return  indexList;
    }

    private List<Lemma> getLemmsOptionTwo(HashMap<String, Integer> lemsFromPage) {
        Set<String> key=lemsFromPage.keySet();
        List<Lemma> list= lemmaReposytory.findAllByLemmaIn(key);
        for(Lemma lemma:list){
           lemma.setFrequency(lemma.getFrequency()+1);
        }
        return  list;
    }

    private List<Lemma> getListLemmsForSaveBase(HashMap<String, Integer> nameLemmaNoYetWriteBase, Site site, List<Lemma> lemmaFromBase) {
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

    private Page getPage(String pathToBase, Site site, String[] resPars) {
        Page page = new Page();
        page.setCode(Integer.valueOf(resPars[0]));
        page.setPath(pathToBase);
        page.setContent(resPars[1]);
        page.setSite(site);
        return  page;
    }

    private String[] getCodeAndContent(String pathToBase) {
        String[] res = new String[2];
        Document document = null;
        try {
            document = Jsoup.connect(pathToBase)
                    .userAgent(userAgent.getUserAgent())
                    .referrer(userAgent.getReferrer())
                    .get();
            res[0] = String.valueOf(document.connection().response().statusCode());
            res[1] = document.head().toString()+document.body().toString().toString();

        } catch (IOException e) {
            logger.error("Error pars " + pathToBase + "  " + e);
        }
        return res;
    }

    private Site getSite(String pathToBase) {
        for(searchengine.config.Site site: siteList.getSites()){
            if(pathToBase.contains(site.getUrl())){
                return siteReposytory.findByUrl(site.getUrl());
            }
        }
        return null;
    }


}
