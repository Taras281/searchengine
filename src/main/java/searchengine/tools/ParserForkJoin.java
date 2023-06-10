package searchengine.tools;

import lombok.AllArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import searchengine.config.Label;
import searchengine.config.UserAgent;
import searchengine.tools.lemmatization.Lemmatizator;
import searchengine.model.Page;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.config.StatusEnum;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;
@AllArgsConstructor
public class ParserForkJoin extends RecursiveTask<Set<String>> {

    PageRepository pageReposytory;

    SiteRepository siteRepository;

    Logger logger;

    private Lemmatizator lemmatizatorServiсe;

    private Label myLabel;

    private UserAgent userAgent;
    private CopyOnWriteArraySet<String> notEyetParsingLinkWhereStopedUser;
    private String link;
    public static Boolean blockWorkForkJoin;
    private String marker;
    private searchengine.model.Site site;
    private Set<String> errorLinks;

    public searchengine.model.Site getSite(){
        return site;
    }


    @Override
    protected Set<String> compute() {
        Set<String> resultSet = new HashSet<>();
        if(!blockWorkForkJoin){
            resultSet.add(link);
            Set<String> links = pars(link);
            List<ParserForkJoin> parserTask = new ArrayList<>();
            for(String l:links){
                ParserForkJoin p = new ParserForkJoin(pageReposytory, siteRepository, logger, lemmatizatorServiсe, myLabel,
                                                      userAgent, notEyetParsingLinkWhereStopedUser, l, marker, site, errorLinks);
                parserTask.add(p);
                p.fork();
            }
            for(ParserForkJoin p:parserTask){
                resultSet.addAll(p.join());
            }
        }
        return resultSet;
    }

    private synchronized Set<String> pars(String url){
        Set<String> setUrlinPage = new HashSet<>();
        Document document = getDocument(url);
        if (document == (null)) {
            notEyetParsingLinkWhereStopedUser.remove(url);
            errorLinks.add(url);
            return setUrlinPage;
        }
        int statusResponse = document.connection().response().statusCode();
        String urlForPath="";
        try {
            urlForPath = remoovePrefix(site,url);
        } catch (ExceptionNotUrl e) {
            return setUrlinPage;
        }
        Page page = null;
        page = new Page(1, site, urlForPath,
                statusResponse, document.head().toString()+document.body().toString());

        if (dontContained((page)))
        {
            try {
                synchronized (pageReposytory){
                    page = pageReposytory.save(page);
                }
            }
            catch (Exception sqe){
                logger.error("sqe "+ sqe);
            }
            setTime(site);
            startLematization(page);
        }
        else{
            notEyetParsingLinkWhereStopedUser.remove(url);
            return setUrlinPage;
        }
        setUrlinPage = getSetUrlInPage(document);
        setUrlinPage.add(url);
        setUrlinPage = reduseList(setUrlinPage, site);

        if(!blockWorkForkJoin){
            notEyetParsingLinkWhereStopedUser.addAll(setUrlinPage);
            notEyetParsingLinkWhereStopedUser.remove(url);
        }
        return setUrlinPage;
    }

    private Set reduseList(Set<String> setUrlinPage, searchengine.model.Site site) {
        String prefix = site.getUrl();
        Set<String> urlSetWithoutPrefix = new HashSet<>();
        for(String url: setUrlinPage){
            try {
                urlSetWithoutPrefix.add(remoovePrefix(site, url));
            } catch (ExceptionNotUrl e) {
                continue;
            }
        }
        Set<Page> pageInBase=null;
        try{
            pageInBase = pageReposytory.findAllBySiteAndPathIn(site, urlSetWithoutPrefix);
        }
        catch (Exception ecp)
        {
            logger.error("PAGEINBASE" + ecp);
        }

        Set<String> listUrlInBase = new HashSet<>();
        if(pageInBase.size()>0){
            for(Page pageinbase: pageInBase){
                listUrlInBase.add(pageinbase.getPath());
            }
        }
        urlSetWithoutPrefix.removeAll(listUrlInBase);
        Set<String> result = new HashSet<>();
        for (String link: urlSetWithoutPrefix){
            result.add(prefix+link);
        }
        return result;
    }

    private String remoovePrefix(searchengine.model.Site site, String url) throws ExceptionNotUrl {
        if (!url.contains(site.getUrl())) {
            throw new ExceptionNotUrl();
        }
        String result=url.replaceAll(site.getUrl(),"");
        return result;
    }

    private Set getSetUrlInPage(Document document) {
        Elements links = document.select("a[href]");
        Set<String> linkss= links.stream().map(link-> link.attr("abs:href")).collect(Collectors.toSet());
        Set<String> res = getSetLinkAfterCheckConsistBase(linkss).stream()
                .filter(link->(contaning(link, marker) && validUrl(link)
                        && !link.isEmpty()&&!errorLinks.contains(link)&& !notEyetParsingLinkWhereStopedUser.contains(link)
                        && dontCrazyYear(link))).collect(Collectors.toSet());
        return res;
    }
    public boolean dontCrazyYear(String link) {
        String regex = ".+\\?.+year=\\d{4}.*";
        int year = LocalDateTime.now().getYear();
        String s=String.valueOf(year);
        if(link.matches(regex)){
            int index= link.indexOf("year=")+5;
            s = link.substring(index,index+4);

        }
        boolean res = Integer.parseInt(s)>year+1?false:true;
        boolean res2 = Integer.parseInt(s)<year-1?false:true;
        return (res&&res2);
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
                sendErrorBase(error);
                errorLinks.add(url);
            }
        }
        return document;
    }

    private void startLematization(Page page) {
        synchronized (lemmatizatorServiсe){
            lemmatizatorServiсe.newStartLematization(page);
        }
    }

    public boolean contaning(String absLink, String marker) {
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
        boolean res = url.matches("https?://.*")&&!url.matches(".*#")&&!url.matches(".*(jpg|jpeg|JPG|png|bmp|pdf|mp4|WebM|js)");
        return res;
    }


    public void stopIndexing() throws InterruptedException, ConcurrentModificationException {
        blockWorkForkJoin=true;
        synchronized (pageReposytory){
            synchronized (notEyetParsingLinkWhereStopedUser){
                for(String url: notEyetParsingLinkWhereStopedUser){
                    Page p=null;
                    try { p = new Page((int)pageReposytory.count()+1, site, remoovePrefix(site,url),
                            418, myLabel.getIndexingStopedUser());
                    } catch (ExceptionNotUrl e) {
                        continue;
                    }
                    if(dontContained(p)){
                        pageReposytory.save(p);
                        sendErrorBase("Stopped indexing user");
                    }
                }
            }
        }
        setStatusSiteIndexed(site);
    }

    private void setStatusSiteIndexed(searchengine.model.Site site){
        synchronized (siteRepository){
            site.setStatus(StatusEnum.INDEXED);
            siteRepository.save(site);
        }
    }

    private void setTime(searchengine.model.Site site) {
        synchronized (siteRepository){
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }
    private String getDataTime() {
        Calendar cal = Calendar.getInstance();
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String result = formatter.format(cal.getTime());
        return result;
    }
    private void sendErrorBase(String error){
        synchronized (siteRepository){
            String err = error;
            site.setLastError(err);
            siteRepository.save(site);
        }
    }
    private boolean dontContained(Page page) {
        Page p=null;
        try{
            p= pageReposytory.findByPathAndSite(page.getPath(), page.getSite());
        }
        catch (Exception ecp)
        {
            logger.error("dontContained " + ecp);
        }
        return p==null?true:false;
    }

    private Set<String> getSetLinkAfterCheckConsistBase(Set<String> inputSet){
        Set<Page> linkExistIntoBase=null;
        try{
            linkExistIntoBase = pageReposytory.findAllByPathIn(inputSet);
        }
        catch (Exception e){
            logger.error("Exc getSetLinkAfterCheckConsistBase" + e);
        }
        for(Page s: linkExistIntoBase){
            inputSet.remove(s.getPath());
        }
        return inputSet;
    }
}