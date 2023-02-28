package searchengine.services;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.config.USerAgent;
import searchengine.dto.responce.IndexingResponse;
import searchengine.dto.responce.IndexingResponseNotOk;
import searchengine.dto.responce.IndexingResponseOk;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.reposytory.IndexReposytory;
import searchengine.reposytory.LemmaReposytory;
import searchengine.reposytory.PageReposytory;
import searchengine.reposytory.SiteRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class LemmatizatorServiseImplTreeple implements LematizatorServise, Runnable{

    @PersistenceContext
    private EntityManager entityManager;



    private String pathParsingLink;
    @Autowired
    IndexReposytory indexReposytory;

    @Autowired
    LemmaReposytory lemmaReposytory;

    @Autowired
    PageReposytory pageReposytory;

    @Autowired
    SiteRepository siteReposytory;

    @Autowired
    Lematizator lematizator;
    @Autowired
    SitesList siteList;

    @Autowired
    USerAgent userAgent;

    @Autowired
    SitesList sitesList;

    @Autowired
    IndexingResponseNotOk indexingResponseNotOk;

    @Autowired
    IndexingResponseOk indexingResponseOk;

    public void setRewritePage(boolean rewritePage) {
        this.rewritePage = rewritePage;
    }


    public ResponseEntity<IndexingResponse> getResponse(String uri) {
        if (uriContainsSiteList(uri))
        {   this.setPathParsingLink(uri);
            this.setRewritePage(true);
            this.run();
            indexingResponseOk.setResult(true);
            return new ResponseEntity<>(indexingResponseOk, HttpStatus.OK);}
        else{
            indexingResponseNotOk.setError("Данная страница находится за пределами сайтов, " +
                                            "указанных в конфигурационном файле");
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.BAD_REQUEST);
       }
     }
    private boolean uriContainsSiteList(String uri) {
        return sitesList.getSites().stream().map(l-> uri.contains(l.getUrl())).anyMatch(b->b==true);
    }

    private boolean rewritePage;

    @Override
    public void run() {
        writeBaseLemsTableIndex(pathParsingLink);
    }
    @Override
    public void setPathParsingLink(String pathParsingLink) {
        this.pathParsingLink = pathParsingLink;

    }

    @Override
    public void writeBaseLemsTableIndex(String pathToBase) {
            Page page = pageReposytory.findByPath(pathToBase);
            Site site = getSite(pathToBase);
            pageReposytory.flush();
            siteReposytory.flush();
            if(rewritePage){
                if(page!=null){
                delite(page);}
                if(site==null){
                    return;
                }
                String[] resPars=getCodeAndContent(pathToBase);
                List<Site> sites = siteReposytory.findAll();
                pageReposytory.save(getPage(pathToBase, site, resPars));
                page = pageReposytory.findByPath(pathToBase);
            }
            if (page==null){
                return;
            }
        String content = page.getContent();
        HashMap<String, Integer> lemsFromPage = lematizator.getLems(content);
        // надо получить все леммы и индексы которые есть в базе
        // леммы получить по списку лем со страници
        // при создании индексов сначала создать для уже существующих лемм, а затем для новых лемм
        // которых нет записать (создать список еще не записанных лемм. и из них слелать список индексов)
        List<Lemma> lemmaFromBase = getLemmsOptionTwo(lemsFromPage);
        HashMap<String, Integer> nameLemmaNoYetWriteBase = getLemsNotYetWriteBase(lemsFromPage, lemmaFromBase);
        List<Lemma> lemmaNoYetWriteBase = getListLemmsForSaveBase(nameLemmaNoYetWriteBase, site, lemmaFromBase);
        List<Lemma> listLemmaForSaveBase = (lemmaFromBase);
        listLemmaForSaveBase.addAll(lemmaNoYetWriteBase);
        List<Index> indexFromBaseOptionTwoo = getIndex(listLemmaForSaveBase, page);
        entityManager.flush();
        lemmaReposytory.saveAll(listLemmaForSaveBase);
        indexReposytory.saveAll(indexFromBaseOptionTwoo);
    }

    private void delite(Page page) {
        List<Index> indexList = indexReposytory.findAllByPageId(page);
        pageReposytory.delete(page);
        pageReposytory.flush();
 }

    private Index getIndex(Lemma lemma, Page page) {
        Index index = new Index();
        index.setRank(lemma.getFrequency());
        index.setLemmaId(lemma);
        index.setPageId(page);
        return  index;
    }
    private List<Index> getIndex(List<Lemma> lemmaFromBase, Page page) {
        // ищеем Леммы для которых нет индексов
        // получаем список индексов для существующих лемм
        // получаем Леммы для которых нет индексов, делаем из них индексы
       /* ArrayList<Integer> lemmaIds = (ArrayList<Integer>) lemmaFromBase.stream().map(lemma -> lemma.getId()).collect(Collectors.toList());
        List<Index> indexList = indexReposytory.getAllContainsLems(lemmaIds);
        List<Integer> indexLemm = indexList.stream().map(index -> index.getLemmaId().getId()).collect(Collectors.toList());
        List<Integer> idLemmaNotHaveIndex = lemmaFromBase.stream().map(lemma -> lemma.getId())
                .filter(l->!indexLemm.contains(l)).collect(Collectors.toList());
        List<Lemma> LemmaNotHaveIndex = lemmaFromBase.stream().filter(lemma -> idLemmaNotHaveIndex.contains(lemma.getId())).collect(Collectors.toList());
        for (Lemma l: LemmaNotHaveIndex){
            indexList.add(getIndex(l, page));
        }*/
        List<Index> indexList = new ArrayList<>();
        for(Lemma lemma: lemmaFromBase){
            indexList.add(getIndex(lemma, page));
        }
        return  indexList;
    }

    private List<Lemma> getLemmsOptionTwo(HashMap<String, Integer> lemsFromPage) {
        Set<String> key=lemsFromPage.keySet();
        List<Lemma> list= lemmaReposytory.getAllContainsLems(key);
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


    @Override
    public List<Lemma> writeBaseLemsToTableLemma(Page page, Site site, HashMap<String, Integer> counterLems) {
        return null;
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
