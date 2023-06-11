package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.*;
import searchengine.dto.responce.IndexingResponse;
import searchengine.dto.responce.IndexingResponseNotOk;
import searchengine.dto.responce.IndexingResponseOk;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.tools.ParserForkJoinAction;
import searchengine.tools.lemmatization.Lemmatizator;
import searchengine.model.Page;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.tools.lemmatization.LemmatizatorReturnCountWord;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class IndexingServiceImpl implements IndexingService {

    private final SiteRepository siteRepository;

    private final PageRepository pageRepository;
    private  final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private UserAgent userAgent;

    private SitesList sitesList;

    private Logger logger;
    private ForkJoinPool forkJoinPool;
    private ParserForkJoinAction parserForkJoinAction;
    private ArrayList<ParserForkJoinAction> listTask;
    private Lemmatizator lemmatizatorServiсe;
    private Label myLabel;
    private Set<Long> siteIdListForCheckStopped;
    private Set<Page> pages;
    LemmatizatorReturnCountWord lemmatizator;
    public IndexingServiceImpl(SiteRepository siteRepository, PageRepository pageReposytory,
                               UserAgent userAgent, SitesList sitesList, Label label, Logger logger,
                               LemmatizatorReturnCountWord lemmatizator, LemmaRepository lemmaRepository,
                               IndexRepository indexRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageReposytory;
        this.sitesList = sitesList;
        this.myLabel = label;
        this.userAgent = userAgent;
        this.logger = logger;
        this.lemmatizator = lemmatizator;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    public ResponseEntity<IndexingResponse> getStartResponse(){
        if(siteRepository.findByStatus(StatusEnum.INDEXING).size()<1){
            siteIdListForCheckStopped = new HashSet<>();
            pages = new HashSet<>();
            startIndexing();
            return new ResponseEntity<>(createIndexingResponseOk(), HttpStatus.OK);
        }
        return  new ResponseEntity<>(createResponseNotOk(myLabel.getIndexingStarted()), HttpStatus.OK);
    }

    public ResponseEntity<IndexingResponse> getStopIndexing() throws InterruptedException {
        if(siteRepository.findByStatus(StatusEnum.INDEXING).size()>0){
            synchronized (siteRepository){
                ArrayList<searchengine.model.Site> sites = siteRepository.findByStatus(StatusEnum.INDEXING);
                for(searchengine.model.Site site: sites){
                    site.setStatus(StatusEnum.FAILED);
                    site.setLastError("Индексация остановлена пользователем");
                    siteIdListForCheckStopped.add(site.getId());
                }
                siteRepository.saveAll(sites);
            }
            return new ResponseEntity<>(createIndexingResponseOk(), HttpStatus.OK);
        }
        else {
            return new ResponseEntity<>(createResponseNotOk(myLabel.getIndexingNotStarted()), HttpStatus.OK);
        }
    }

    public ResponseEntity<IndexingResponse> getResponseIndexing(String uri) {
        Page page = getPageFromUri(uri);
        IndexingResponseNotOk indexingResponseNotOk = new IndexingResponseNotOk();
        IndexingResponseOk indexingResponseOk = new IndexingResponseOk();
        if(!validUrl(uri)){
            indexingResponseNotOk.setResult(false);
            indexingResponseNotOk.setError("Проверте правильность написания адреса");
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.OK);
        }
        if (page==null){
            indexingResponseNotOk.setResult(false);
            indexingResponseNotOk.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.OK);
        }
        if (uriContainsSiteList(uri)) {
            savePageBase(page);
            indexingResponseOk.setResult(true);
            return new ResponseEntity<>(indexingResponseOk, HttpStatus.OK);}
        else{
            indexingResponseNotOk.setError(myLabel.getThisPageOutSite());
            return new ResponseEntity<>(indexingResponseNotOk, HttpStatus.BAD_REQUEST);
        }
    }
    private void startIndexing() {
        forkJoinPool =  new ForkJoinPool();
        listTask = new ArrayList<>();
        siteRepository.deleteAll();
        for(Site site: sitesList.getSites()) {
            String url = site.getUrl();
            searchengine.model.Site siteForBase = returnModelSite(StatusEnum.INDEXING, getDataTime(), "", url, site.getName());
            searchengine.model.Site siteFromBase=null;
            synchronized (siteRepository) {
                siteFromBase = siteRepository.save(siteForBase);
            }
            parserForkJoinAction =  new ParserForkJoinAction(siteFromBase,"",this, new HashSet<>(), userAgent);
            listTask.add(parserForkJoinAction);
        }
        for (ParserForkJoinAction pfj: listTask){
            new Thread(()->{
                forkJoinPool.invoke(pfj);
                if(!siteIdListForCheckStopped.contains(pfj.getSite().getId()))
                {
                    searchengine.model.Site site = pfj.getSite();
                    site.setStatus(StatusEnum.INDEXED);
                    siteRepository.save(site);}
            }).start();
        }
    }
    @Override
    public void addBase(Page newPage) {
        synchronized (pageRepository){
        if(siteIdListForCheckStopped.contains(newPage.getSite().getId())){
           newPage.getSite().setStatus(StatusEnum.FAILED);
        }
            savePageBase(newPage);
        }
    }

    /*private void saveDataBase(Set<Page> pages){
        pageRepository.saveAll(pages);
        pages.clear();
    }*/
    private void savePageBase(Page page){
        page = pageRepository.save(page);
        synchronized (lemmaRepository){
            synchronized (indexRepository){
                writeBaseLemmsTableIndex(page);
            }
        }
    }

    public void writeBaseLemmsTableIndex(Page page) {
        searchengine.model.Site site = page.getSite();
        String content = page.getContent();
        HashMap<String, Integer> lemmsFromPage = lemmatizator.getLems(content);
        List<Lemma> lemmaFromBase = getLemmsFromBase(lemmsFromPage);
        HashMap<String, Integer> nameLemmaNoYetWriteBase = getLemmsNotYetWriteBase(lemmsFromPage, lemmaFromBase);
        List<Lemma> lemmaNoYetWriteBase = getListLemmsForSaveBase(nameLemmaNoYetWriteBase, site);
        lemmaFromBase.addAll(lemmaNoYetWriteBase);
        List<Index> indexFromBase = getIndex(lemmaFromBase, lemmsFromPage, page);

        lemmaRepository.saveAll(lemmaFromBase);
        indexRepository.saveAll(indexFromBase);
    }
    private List<Index> getIndex(List<Lemma> lemmaFromBase, HashMap<String, Integer> lemsFromPage, Page page) {
        List<Index> indexList = new ArrayList<>();
        for(Lemma lemma: lemmaFromBase){
            int rank = lemsFromPage.get(lemma.getLemma());
            indexList.add(getIndex(lemma, page, rank));
        }
        return  indexList;
    }
    private Index getIndex(Lemma lemma, Page page, int rank) {
        Index index = new Index();
        index.setRank(rank);
        index.setLemmaId(lemma);
        index.setPageId(page);
        return  index;
    }
    private synchronized List<Lemma> getLemmsFromBase(HashMap<String, Integer> lemsFromPage) {
        Set<String> keys=lemsFromPage.keySet();
        List<Lemma> list= lemmaRepository.findAllByLemmaIn(keys);
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
    private List<Lemma> getListLemmsForSaveBase(HashMap<String, Integer> nameLemmaNoYetWriteBase, searchengine.model.Site site) {
        List<Lemma> result = new ArrayList<>();
        for(Map.Entry<String, Integer> entryLemma: nameLemmaNoYetWriteBase.entrySet()){
            result.add(getLemma(entryLemma, site));
        }
        return result;
    }
    private Lemma getLemma(Map.Entry<String, Integer> entryLemma, searchengine.model.Site site) {
        Lemma lemma = new Lemma();
        lemma.setLemma(entryLemma.getKey());
        lemma.setFrequency(1);
        lemma.setSite(site);
        return lemma;
    }
    private HashMap<String, Integer> getLemmsNotYetWriteBase(HashMap<String, Integer> lemmsFromPage, List<Lemma> lemmaFromBase) {
        Set<String> lemms = lemmsFromPage.keySet();
        HashMap<String, Integer> resultLemms = new HashMap<>();
        List<String> lemmsFromBase = lemmaFromBase.stream().map(lemma -> lemma.getLemma()).collect(Collectors.toList());
        for(String lemma: lemms){
            if(!lemmsFromBase.contains(lemma)){
                resultLemms.put(lemma, lemmsFromPage.get(lemma));
            }
        }
        return  resultLemms;

    }
    @Override
    public Set<Page> getPages(searchengine.model.Site site, Set<String> links) {
        return pageRepository.findAllBySiteAndPathIn(site, links);
    }
    @Override
    public void saveError(searchengine.model.Site site, String s) {
        synchronized (siteRepository){
        site.setLastError(s);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }}

    @Override
    public StatusEnum getStatus(searchengine.model.Site site) {
        return siteRepository.findByUrl(site.getUrl()).getStatus();
    }

    private IndexingResponse createIndexingResponseOk() {
        IndexingResponseOk indexingResponseOk =  new IndexingResponseOk();
        indexingResponseOk.setResult(true);
        return indexingResponseOk;
    }
    private IndexingResponse createResponseNotOk(String error) {
        IndexingResponseNotOk indexingResponseNotOk = new IndexingResponseNotOk();
        indexingResponseNotOk.setResult(false);
        indexingResponseNotOk.setError(error);
        return indexingResponseNotOk;
    }




    private searchengine.model.Site returnModelSite(StatusEnum indexing, String dataTime, String error, String url, String nameSite) {
        searchengine.model.Site site=siteRepository.findByUrl(url);
        if(site==null){
            site = new searchengine.model.Site();
            site.setStatus(indexing);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(error);
            site.setUrl(url);
            site.setName(nameSite);
        }
        return site;
    }
    private String getDataTime() {
        Calendar cal = Calendar.getInstance();
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String result = formatter.format(cal.getTime());
        return result;
    }
    public boolean validUrl(String uri) {
        return  uri.matches("^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");
    }
    private Page getPageFromUri(String uri) {
        ArrayList<String> listUri = (ArrayList<String>) sitesList.getSites().stream().map(s->s.getUrl()).collect(Collectors.toList());
        Page p=null;
        searchengine.model.Site s;
        for(String site: listUri){
            if(uri.contains(site)){
                s = siteRepository.findByUrl(site);
                String urlForPath = null;
                urlForPath = remoovePrefix(s, uri);
                p = pageRepository.findByPathAndSite(urlForPath, s);
                if(p!=null){
                    pageRepository.delete(p);
                }
                Document document = getDocument(s, urlForPath);
                int statusResponse = document.connection().response().statusCode();
                p =  new Page(1, s, urlForPath, statusResponse, document.html());
                p = pageRepository.save(p);

            }
        }
        return p;
    }
    private boolean uriContainsSiteList(String uri) {
        return sitesList.getSites().stream().map(l-> uri.contains(l.getUrl())).anyMatch(b->b==true);
    }
    public String remoovePrefix(searchengine.model.Site site, String url){
        String result=url.replaceAll(site.getUrl(),"");
        return result;
    }
    public Document getDocument(searchengine.model.Site site, String urlPage) {
        Document document = null;
        try {
            document = Jsoup.connect(site.getUrl().concat(urlPage))
                    .userAgent(userAgent.getUserAgent())
                    .referrer(userAgent.getReferrer())
                    .get();
        } catch (IOException e) {
            synchronized (urlPage){
                addBase(new Page(1, site, urlPage, 522,""));
                saveError(site, "Error pars url code = "+ 522 + " " + site.getUrl().concat(urlPage) + "  " +e.toString());
            }
        }
        return document;
    }


}

