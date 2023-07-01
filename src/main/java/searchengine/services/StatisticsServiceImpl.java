package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.config.StatusEnum;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
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
        for (Site site : sitesList) {
            List<searchengine.model.Site> siteModelList = siteRepository.findByName(site.getName());
            if (siteModelList.size() > 1) {
                return getErrorBaseTableSite("База вернула не однозначный ответ(список сайтов) позовите системного администратора", StatusEnum.FAILED);
            }
            searchengine.model.Site siteModel;
            try {
                siteModel = siteModelList.get(0);
            } catch (IndexOutOfBoundsException ibe) {
                return getErrorBaseTableSite("Таблица  site  еще не заполнена", StatusEnum.FAILED);
            }
            if (siteModel == null) {
                continue;
            }
            detailed.addAll(addDetailedAndTotalStatistics(site, siteModel, total));
        }
        statisticsData.setTotal(total);
        statisticsData.setDetailed(detailed);
        return statisticsData;
    }

    private List<DetailedStatisticsItem> addDetailedAndTotalStatistics(Site site, searchengine.model.Site siteModel, TotalStatistics total) {
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(site.getName());
        item.setUrl(site.getUrl());
        int pages = pageRepository.countBySite(siteModel);
        if(pages>0){
            int numLemms = lemmaRepository.countBySite(siteModel);
            setParametrsItem(item, pages, numLemms, siteModel);
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + numLemms);
            detailed.add(item);
        }
        return detailed;
    }

    private void setParametrsItem(DetailedStatisticsItem item, int pages, int lemmas, searchengine.model.Site  siteModel) {
        item.setPages(pages);
        item.setLemmas(lemmas);
        item.setStatus(siteModel.getStatus());
        item.setError(siteModel.getLastError());
        item.setStatusTime(siteModel.getStatusTime());
    }
    private StatisticsData getErrorBaseTableSite(String myErrorStatus, StatusEnum statusEnum) {
        DetailedStatisticsItem detailedStatisticsItem = new DetailedStatisticsItem();
        detailedStatisticsItem.setError(myErrorStatus);
        detailedStatisticsItem.setStatus(statusEnum);
        List<DetailedStatisticsItem> error = new ArrayList<>();
        error.add(detailedStatisticsItem);
        TotalStatistics totalStatistics = new TotalStatistics();
        StatisticsData statisticsData1 = new StatisticsData();
        statisticsData1.setDetailed(error);
        statisticsData1.setTotal(totalStatistics);
        return statisticsData1;
    }

}
