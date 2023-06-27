package searchengine.services;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.*;
import searchengine.dto.response.IndexingResponse;
import searchengine.dto.response.IndexingResponseNotOk;
import searchengine.dto.response.IndexingResponseOk;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.tools.ParserForkJoinAction;
import searchengine.model.Page;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.tools.Lemmatizator;
import java.io.IOException;
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
    private Set<Long> siteIdListForCheckStopped;
    private Set<Page> pages;
    private Lemmatizator lemmatizator;
    public IndexingServiceImpl(SiteRepository siteRepository, PageRepository pageReposytory,
                               UserAgent userAgent, SitesList sitesList, Logger logger,
                               Lemmatizator lemmatizator, LemmaRepository lemmaRepository,
                               IndexRepository indexRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageReposytory;
        this.sitesList = sitesList;
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
        return  new ResponseEntity<>(createResponseNotOk("Индексация уже запущена"), HttpStatus.OK);
    }

    public ResponseEntity<IndexingResponse> getStopIndexing(){
        if(siteRepository.findByStatus(StatusEnum.INDEXING).size()>0){
            synchronized (siteRepository){
                ArrayList<searchengine.model.Site> sites = siteRepository.findByStatus(StatusEnum.INDEXING);
                for(searchengine.model.Site site: sites){
                    site.setStatus(StatusEnum.FAILED);
                    site.setLastError("Индексация остановлена пользователем");
                    siteIdListForCheckStopped.add(site.getId());
                }
                siteRepository.saveAll(sites);
                while (!(forkJoinPool.getActiveThreadCount()<=0)) {
                }
            }
            return new ResponseEntity<>(createIndexingResponseOk(), HttpStatus.OK);
        }
        else {
            return new ResponseEntity<>(createResponseNotOk("Индексация ещё не запущена"), HttpStatus.OK);
        }
    }
    public ResponseEntity<IndexingResponse> getResponseIndexing(String uri) {
        if(!validUrl(uri)){
            return new ResponseEntity<>(createResponseNotOk("Проверте правильность написания адреса"), HttpStatus.OK);
        }
        Page page = getPageFromUri(uri);
        if (page==null){
            return new ResponseEntity<>(createResponseNotOk("Данная страница находится за пределами сайтов, указанных в конфигурационном файле"), HttpStatus.OK);
        }
            savePageBase(page);
            return new ResponseEntity<>(createIndexingResponseOk(), HttpStatus.OK);
    }
    private void startIndexing() {
        forkJoinPool =  new ForkJoinPool();
        listTask = new ArrayList<>();
        deleteAll();
        for(Site site: sitesList.getSites()) {
            String url = site.getUrl();
            searchengine.model.Site siteForBase = returnModelSite(StatusEnum.INDEXING,  "", url, site.getName());
            searchengine.model.Site siteFromBase=null;
            synchronized (siteRepository) {
            siteFromBase = siteRepository.save(siteForBase);
            }
            parserForkJoinAction = new ParserForkJoinAction(siteFromBase,"",this, new HashSet<>(), userAgent);
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

    private void deleteAll(){
        siteRepository.deleteAll();
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
    private void savePageBase(Page page){
        page = pageRepository.save(page);
        if(page.getCode()>299){
            return;
        }
        synchronized (lemmaRepository){
            synchronized (indexRepository){
                writeBaseLemmsTableIndex(page);
            }
        }
    }
    public void writeBaseLemmsTableIndex(Page page) {
        searchengine.model.Site site = page.getSite();
        String content = page.getContent();
        HashMap<String, Integer> lemmsFromPage = lemmatizator.getLemmsInPage(content);
        List<Lemma> lemmaFromBase = getLemmsFromBase(lemmsFromPage);
        HashMap<String, Integer> nameLemmaNotYetWriteBase = getLemmsNotYetWriteBase(lemmsFromPage, lemmaFromBase);
        List<Lemma> lemmaNotYetWriteBase = getListLemmsForSaveBase(nameLemmaNotYetWriteBase, site);
        lemmaFromBase.addAll(lemmaNotYetWriteBase);
        List<Index> indexFromBase = getIndex(lemmaFromBase, lemmsFromPage, page);
        lemmaRepository.saveAll(lemmaFromBase);
        indexRepository.saveAll(indexFromBase);
    }
    private List<Index> getIndex(List<Lemma> lemmaFromBase, HashMap<String, Integer> lemmsFromPage, Page page) {
        List<Index> indexList = new ArrayList<>();
        for(Lemma lemma: lemmaFromBase){
            int rank = lemmsFromPage.get(lemma.getLemma());
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
    private synchronized List<Lemma> getLemmsFromBase(HashMap<String, Integer> lemmsFromPage) {
        Set<String> keys=lemmsFromPage.keySet();
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
        List<String> lemmsFromBase = lemmaFromBase.stream().map(Lemma::getLemma).collect(Collectors.toList());
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
    public void saveError(searchengine.model.Site site, String error) {
        synchronized (siteRepository){
        site.setLastError(error);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }}
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
    private searchengine.model.Site returnModelSite(StatusEnum indexing, String error, String url, String nameSite) {
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
    public  boolean validUrl(String uri) {
        return  !uri.matches(".*((\\.jpg)|(\\.jpeg)|(\\.pdf)|(\\.PDF)|(\\.JPEG)|(\\.JPG)|(\\.doc)|(\\.xml)" +
                                   "|(\\.mp3)|(\\.MP3)|(\\.mp4)|(\\.MP4)|(\\.AVI)|(\\.avi)|(\\.wav)|(\\.WAV)|((/.*#.*)$))");
    }
    private Page getPageFromUri(String uri) {
        ArrayList<String> listUri = (ArrayList<String>) sitesList.getSites().stream().map(site -> site.getUrl()).collect(Collectors.toList());
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
        }
        catch (IOException exception){
            try{
                document = Jsoup.connect(site.getUrl().concat(urlPage)).get();
            }
            catch (HttpStatusException hse){
            addBase(new Page(1, site, urlPage, hse.getStatusCode(),""));
            saveError(site, hse.getMessage());}
            catch (IOException e) {
            addBase(new Page(1, site, urlPage, 418,""));
            saveError(site, "eror parse code 418 url " + site.getUrl().concat(urlPage) + "  " + e.toString());
            }
        }

        return document;
    }
}

