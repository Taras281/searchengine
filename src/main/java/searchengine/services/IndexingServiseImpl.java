package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.context.ApplicationContext;
import searchengine.config.ApplicationContextHolder;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.config.USerAgent;
import searchengine.model.Page;
import searchengine.model.StatusEnum;
import searchengine.reposytory.PageReposytory;
import searchengine.reposytory.SiteRepository;

import java.io.IOException;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


public class IndexingServiseImpl implements IndexingServise  {
    private  int numberSiteApplicationProperties;

    private SitesList sitesListFromProperties;
    private USerAgent userAgent;
    private SiteRepository siteRepository;
    private PageReposytory pageReposytory;
    private ForkJoinPool fjp;
    public static AtomicBoolean recreating = new AtomicBoolean();

    private long siteId;
    private Site site;

    private String marker;
    private Set notEyetParsingLinkWhereStopedUser;

    private ParserForkJoin task;

    public void init(Site site, SitesList sitesList, USerAgent userAgent, SiteRepository siteRepository, PageReposytory pageReposytory, int numberSiteApplicationProperties){
        this.site = site;
        this.sitesListFromProperties = sitesList;
        this.userAgent = userAgent;
        this.siteRepository = siteRepository;
        this.pageReposytory=pageReposytory;
        this.siteId = numberSiteApplicationProperties;
        System.out.println("Init indexingServise"  + Thread.currentThread().getName());
    }

    @Override
    public synchronized boolean startIndexing(){
         recreatingTableSiteAndTable();
         parsing(site.getUrl(), site);
         System.out.println("+ Start parsing " + "numer site " + numberSiteApplicationProperties + " " + Thread.currentThread().getName());
         return true;
    }

    @Override
    public boolean stopIndexing() throws InterruptedException {
        task.blockWorkForkJoin=true;
        task.stopIndexing();
        fjp.shutdownNow();
        return true;

    }
    private boolean parsing(String url, Site site) {
        searchengine.model.Site siteInBAse = returnModelSite(siteId, StatusEnum.INDEXING, getDataTime(), "", url, site.getName());
        siteRepository.save(siteInBAse);
        marker = url;
        notEyetParsingLinkWhereStopedUser = new HashSet();
        task = new ParserForkJoin(url, siteInBAse, marker, notEyetParsingLinkWhereStopedUser);
        task.blockWorkForkJoin=(false);
        long Start = System.currentTimeMillis();
        System.out.println("+++++++ BEFORE execute " + url + Thread.currentThread().getName());
        System.out.println("xxxxxxxxxxx fjp "+fjp.toString());
        fjp.invoke(task);
        siteInBAse.setStatus(StatusEnum.INDEXED);
        siteRepository.save(siteInBAse);
        System.out.println("Time parsing " + (System.currentTimeMillis()-Start)/1000);
        System.out.println("--------- AFTER execute " + url  + Thread.currentThread().getName());
        System.out.println("YYYYYYYYYY fjp "+fjp.toString());
        return true;
    }

    private void recreatingTableSiteAndTable() {
        synchronized (recreating){
            if(recreating.get()){
                recreating.set(false);
                System.out.println("recreating table " + Thread.currentThread().getName());
                siteRepository.dropTableSiteAndPage();
                siteRepository.createTableSite();
                siteRepository.createTablePage();
                siteRepository.createTableLemma();
                siteRepository.createTableIndex();
                fjp=new ForkJoinPool();
                System.out.println("recreating fjp true " + Thread.currentThread().getName());
            }
            else{
                fjp=new ForkJoinPool();
                System.out.println("recreating fjp false" + Thread.currentThread().getName());
            }
        }

    }


    private searchengine.model.Site returnModelSite(long id, StatusEnum indexing, String dataTime, String error, String url, String nameSite) {
        searchengine.model.Site site = new searchengine.model.Site();
        site.setId(id);
        site.setStatus(indexing);
        site.setStatusTime(dataTime);
        site.setLastError(error);
        site.setUrl(url);
        site.setName(nameSite);
        return site;


    }

    private String getDataTime() {
        Calendar cal = Calendar.getInstance();
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String result = formatter.format(cal.getTime());
        return result;
    }


    public class ParserForkJoin extends RecursiveTask<Set<String>>{
        private  Set<String> notEyetParsingLinkWhereStopedUser;
        String link;
        private String marker;
        private static Boolean blockWorkForkJoin;

        private searchengine.model.Site site;
        static private Set<String> errorLinks;





    public ParserForkJoin(String link, searchengine.model.Site site, String marker, Set notEyetParsingLinkWhereStopedUser) {
        this.link = link;
        this.marker = marker;
        this.notEyetParsingLinkWhereStopedUser = notEyetParsingLinkWhereStopedUser;
        if(errorLinks==null){
            errorLinks = new HashSet<>();
        }
        this.site=site;

    }

    @Override
    protected Set<String> compute() {
        Set<String> resultSet = new HashSet<>();
        if(!blockWorkForkJoin){
            resultSet.add(link);
            Set<String> links = pars(link);
            List<ParserForkJoin> parserTask = new ArrayList<>();
            for(String l:links){
                ParserForkJoin p = new ParserForkJoin(l, site, marker, notEyetParsingLinkWhereStopedUser);
                p.fork();
                parserTask.add(p);
            }
            for(ParserForkJoin p:parserTask){
                resultSet.addAll(p.join());
            }
        }
        //System.out.println("resultSet size End  "+ resultSet.size());
        return resultSet;
    }

    private Set<String> pars(String url){
        System.out.println("Start thread " + Thread.currentThread().getName() + " parsing url " + url );
        Set setUrlinPage = new HashSet<>();
        int statusResponse = -1;
        if ((!validUrl(url))||(!contaning(url, marker))|| errorLinks.contains(url)) {
            notEyetParsingLinkWhereStopedUser.remove(url);
            return setUrlinPage;
        }
        Document document = null;
        try {
            document = Jsoup.connect(url)
                    .userAgent(userAgent.getUserAgent())
                    .referrer(userAgent.getReferrer())
                    .get();
            statusResponse = document.connection().response().statusCode();

        } catch (IOException e) {
            synchronized (url){
                sendErrorBase("Error pars url " + url + "  " +e.toString());
                errorLinks.add(url);
            }
        }
        if (document == (null)) {
            notEyetParsingLinkWhereStopedUser.remove(url);
            return setUrlinPage;
        }
        synchronized (pageReposytory){
         if (dontContained((url))) {
                pageReposytory.save(new Page((int) (pageReposytory.count() + 1), site, (url),
                        statusResponse, document.head().toString()+document.body().toString()));
                setTime(site);
                startLematization(url);
        }
         else{
             notEyetParsingLinkWhereStopedUser.remove(url);
             return setUrlinPage;
         }
        }
        Elements links = document.select("a[href]");
        Set<String> linkss= links.stream().map(link-> link.attr("abs:href")).collect(Collectors.toSet());
        setUrlinPage = getSetLinkAfterCheckConsistBase(linkss).
                                     stream().filter(link->(contaning(link, marker) && validUrl(link) && !link.isEmpty())).
                                     collect(Collectors.toSet());

      if(!blockWorkForkJoin){
          notEyetParsingLinkWhereStopedUser.addAll(setUrlinPage);
          notEyetParsingLinkWhereStopedUser.remove(url);
      }

        System.out.println("return thread " + Thread.currentThread().getName() + " parsing url " + url + " Size " +setUrlinPage.size());
        return setUrlinPage;
    }

        private void startLematization(String url) {
            SubLematizatorController.getInstance().addDeque(url);
        }


        public boolean contaning(String absLink, String marker) {
            if (absLink == "/www.playback") {
                int y = 0;
            }

            if (marker.contains("www.")) {
                String[] s = marker.split("www.");
                if (s.length > 1) {
                    marker = s[0] + s[1];
                } else {
                    marker = s[0];
                }
            }
            if (absLink.contains("www.")) {
                String[] s = absLink.split("www.");
                if (s.length > 1) {
                    absLink = s[0] + s[1];
                } else {
                    absLink = s[0];
                }
            }
            return absLink.contains(marker);
        }

    private boolean validUrl(String url) {
        return url.matches("https?://.*")&&!url.matches(".*#")&&!url.matches(".*(jpg|jpeg|JPG|png|bmp|pdf|mp4|WebM|js)");
    }


    private void stopIndexing() throws InterruptedException {
        synchronized (pageReposytory){
            synchronized (notEyetParsingLinkWhereStopedUser){
                for(String url: notEyetParsingLinkWhereStopedUser){
                    if(dontContained(url)){
                        pageReposytory.save(new Page((int)pageReposytory.count()+1, site, url,
                                418, "FAILED Индексация остановлена пользователем"));
                    }
                }
            }

            }
        setStatusSiteINDEXED(site);
        }

        public void setStatusSiteINDEXED(searchengine.model.Site site){
            synchronized (siteRepository){
                site.setStatus(StatusEnum.INDEXED);
                siteRepository.save(site);
            }

        }

        private void setTime(searchengine.model.Site site) {
            synchronized (siteRepository){
                site.setStatusTime(getDataTime());
                siteRepository.save(site);
            }

        }
        private void sendErrorBase(String error){
            //String err = site.getLastError() + System.lineSeparator() + error;
            synchronized (siteRepository){
                String err = error;
                site.setLastError(err);
                siteRepository.save(site);
            }

        }


        private boolean dontContained(String absLink) {
            ArrayList<String> response = pageReposytory.getByPath(absLink);
            return response.isEmpty();
        }

        private Set<String> getSetLinkAfterCheckConsistBase(Set<String> inputSet){
            Set<String> linkExistIntoBase = pageReposytory.getListPageonPath(inputSet);
            for(String s: linkExistIntoBase){
                inputSet.remove(s);
            }
            return inputSet;
        }

    }



}


