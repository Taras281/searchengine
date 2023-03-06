package searchengine.services.statistics;

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
import searchengine.reposytory.LemmaRepository;
import searchengine.reposytory.PageRepository;
import searchengine.reposytory.SiteRepository;

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
    private final Random random = new Random();
    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {
        String[] statuses = {"INDEXED", "FAILED", "INDEXING"};
        String[] errors = {
                "Ошибка индексации: главная страница сайта не доступна",
                "Ошибка индексации: сайт не доступен",
                ""
        };
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        for (int i = 0; i < sitesList.size(); i++) {
            Site site = sitesList.get(i);
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
                searchengine.model.Site siteModel = siteRepository.findByUrl(site.getUrl());
                if(siteModel==null){continue;}
                ArrayList<Page> pagesSite = pageReposytory.findAllBySiteId(siteModel);
                int pages = pagesSite.size();
                if(pages>0){
                    int idSite = (int) siteModel.getId();
                    ArrayList<Lemma> lemma = lemmaReposytory.getAllLems(siteModel);
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
            StatisticsResponse response = new StatisticsResponse();
            StatisticsData data = new StatisticsData();
            data.setTotal(total);
            data.setDetailed(detailed);
            response.setStatistics(data);
            response.setResult(true);
            return response;
    }

}
