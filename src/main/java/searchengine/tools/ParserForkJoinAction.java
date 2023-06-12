package searchengine.tools;

import lombok.AllArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.config.StatusEnum;
import searchengine.config.UserAgent;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.services.IndexingService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;

@AllArgsConstructor
public class ParserForkJoinAction extends RecursiveAction {
    private Site site;
    private String urlPage;
    private IndexingService indexingService;
    private Set<String> allFindLink;
    private UserAgent userAgent;

    @Override
    protected void compute() {
        if( site.getStatus().equals(StatusEnum.FAILED)){
            site.setLastError("Индексация остановлена пользователем");
            site.setStatusTime(LocalDateTime.now());
            indexingService.addBase(new Page(1,site, urlPage, 418, ""));
            return;
        }
        Document document = indexingService.getDocument(site, urlPage);
        if(document!=null){
            indexingService.addBase(getNewPage(site, urlPage, document));
        }else{
            return;
        }
        List<ParserForkJoinAction> listTask = getTasks(site, indexingService, allFindLink, document);
        for(ParserForkJoinAction task:listTask){
            task.join();
        }
    }
    private List<ParserForkJoinAction> getTasks(Site site, IndexingService indexingService, Set<String> allFindLink, Document document) {
        Set<String>  url;
        synchronized (allFindLink){
             url = getSetUrl(site, document);
        }
        List<ParserForkJoinAction> listTask = new ArrayList<>();
        for(String link:url){
            ParserForkJoinAction task = new ParserForkJoinAction(site, link, indexingService, allFindLink, userAgent);
            task.fork();
            listTask.add(task);
        }
        return listTask;
    }
    private Page getNewPage(Site site, String urlPage, Document document) {
        int status = document.connection().response().statusCode();
        site.setStatusTime(LocalDateTime.now());
        return  new Page(1, site, urlPage, status, document.html());
    }
    private Set getSetUrl(Site site, Document document) {
        Elements elementLinks = document.select("a[href]");
        Set<String> links= elementLinks.stream().map(link-> link.attr("abs:href"))
                .filter(l->l.contains(site.getUrl()))
                .filter(l->!allFindLink.contains(l)).collect(Collectors.toSet());
        Set<String> linksForCheckBase = links.stream().map(l->indexingService.remoovePrefix(site,l)).collect(Collectors.toSet());
        List<String> urlPagesFromBase = indexingService.getPages(site, linksForCheckBase).stream().map(page -> page.getPath()).collect(Collectors.toList());
        allFindLink.addAll(links);
        links = links.stream().map(l->indexingService.remoovePrefix(site,l)).filter(l->!urlPagesFromBase.contains(l)).collect(Collectors.toSet());
        Set<String> res = links.stream()
                .filter(link->(indexingService.validUrl(site.getUrl().concat(link))
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

    public  Site getSite(){
        return site;
    }
    private String isSlash(String url){
        if(!url.startsWith("/")){
            StringBuffer sb = new StringBuffer();
            sb.append("/");
            sb.append(url);
            url=sb.toString();
        }
        return url;
    }

}
