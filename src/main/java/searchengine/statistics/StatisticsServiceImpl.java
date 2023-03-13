package searchengine.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    SiteRepository siteRepository;
    @Autowired
    PageRepository pageReposytory;

    @Autowired
    LemmaRepository lemmaReposytory;
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
            searchengine.model.Site siteModel = siteRepository.findByUrl(site.getUrl());
            if(siteModel==null){continue;}
            ArrayList<Page> pagesSite = pageReposytory.findAllBySite(siteModel);
            int pages = pagesSite.size();
            if(pages>0){
                ArrayList<Lemma> lemma = lemmaReposytory.findAllBySite(siteModel);
                int lemmas = lemma.size();
                item.setPages(pages);
                item.setLemmas(lemmas);
                item.setStatus(siteModel.getStatus().name());
                item.setError(siteModel.getLastError());
                item.setStatusTime(siteModel.getStatusTime());
                total.setPages(total.getPages() + pages);
                total.setLemmas(total.getLemmas() + lemmas);
                detailed.add(item);
            }
        }
        statisticsData.setTotal(total);
        statisticsData.setDetailed(detailed);
        return statisticsData;
    }

}
