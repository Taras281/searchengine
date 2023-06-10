package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Page;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    SiteRepository siteRepository;
    @Autowired
    PageRepository pageRepository;
    @Autowired
    LemmaRepository lemmaRepository;
    private String myErrorStatus = "-111";
    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);
        List<Site> sitesList = sites.getSites();
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = getStatisticsData(sitesList, total);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    private StatisticsData getStatisticsData(List<Site> sitesList, TotalStatistics total) {
        StatisticsData statisticsData = new StatisticsData();
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        for (int i = 0; i < sitesList.size(); i++) {
            Site site = sitesList.get(i);
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            List<searchengine.model.Site> siteModelList = siteRepository.findByName(sitesList.get(i).getName());
            if(siteModelList.size()!=1){
                return getErrorBaseTableSite();
            }
            searchengine.model.Site siteModel = siteModelList.get(0);
            if(siteModel==null){continue;}
            ArrayList<Page> pagesSite = pageRepository.findAllBySite(siteModel);
            int pages = pagesSite.size();
            if(pages>0){
                int numberLemms = lemmaRepository.countBySite(siteModel);
                setParametrsItem(item, pages, numberLemms, siteModel);
                total.setPages(total.getPages() + pages);
                total.setLemmas(total.getLemmas() + numberLemms);
                detailed.add(item);
            }
        }
        statisticsData.setTotal(total);
        statisticsData.setDetailed(detailed);
        return statisticsData;
    }

    private void setParametrsItem(DetailedStatisticsItem item, int pages, int lemmas, searchengine.model.Site  siteModel) {
        item.setPages(pages);
        item.setLemmas(lemmas);
        item.setStatus(siteModel.getStatus().name());
        item.setError(siteModel.getLastError());
        item.setStatusTime(siteModel.getStatusTime().toString());
    }

    private StatisticsData getErrorBaseTableSite() {
        DetailedStatisticsItem detailedStatisticsItem = new DetailedStatisticsItem();
        detailedStatisticsItem.setError("База вернула не однозначный ответ(список сайтов) позовите системного администратора");
        detailedStatisticsItem.setStatus(myErrorStatus);
        List<DetailedStatisticsItem> error  = new ArrayList<>();
        error.add(detailedStatisticsItem);
        TotalStatistics totalStatistics = new TotalStatistics();
        StatisticsData statisticsData1 = new StatisticsData();
        statisticsData1.setDetailed(error);
        statisticsData1.setTotal(totalStatistics);
        return statisticsData1;
    }

}
