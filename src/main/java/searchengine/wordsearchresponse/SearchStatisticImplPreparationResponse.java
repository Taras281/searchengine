package searchengine.wordsearchresponse;

import lombok.Data;
import org.apache.lucene.morphology.WrongCharaterException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.search.DetailedStatisticsSearch;
import searchengine.dto.search.SearchResponce;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.SiteRepository;
import searchengine.lemmatization.Lemmatizator;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Data
public class SearchStatisticImplPreparationResponse implements SearchStatistic{
    private String  query;
    private String  site;
    private int  offset;
    private int  limit;
    @Autowired
    private Searcher search;
    @Autowired
    private Lemmatizator lematizator;
    @Autowired
    SiteRepository siteRepository;


    public SearchResponce getStat() {
        if(query.equals("")||query.matches("^[a-zA-Z]+$")){
            SearchResponce responce = new SearchResponce();
            responce.setResult(false);
            responce.setError("Поисковый запрос должен содержать только РУССКИЕ!!! буквы");
            return responce;
        }
        search.setLimit(limit);
        search.setOffset(offset);
        search.setSite(site);
        ArrayList<Map.Entry<Page, float[]>> resultSearch = search.vorker(query);
        if(resultSearch==null){
            SearchResponce responce = new SearchResponce();
            responce.setResult(false);
            responce.setData(getEmptyData());
            responce.setError("результаты не найдены");
            return responce;
        }
        int countPage = resultSearch.size();
        int start = offset>=resultSearch.size()?resultSearch.size()-1:offset;
        int dif = Math.abs(resultSearch.size()-start);
        int stop = limit>dif?dif:limit;
        DetailedStatisticsSearch[] detailedStatisticsSearches = new DetailedStatisticsSearch[dif];
        for(int i = start; i<start+stop; i++){
            DetailedStatisticsSearch item = new DetailedStatisticsSearch();
            item.setRelevance(resultSearch.get(i).getValue()[0]);
            item.setTitle(getTitle(resultSearch.get(i).getKey().getContent()));
            Optional<Site> site = siteRepository.findById(resultSearch.get(i).getKey().getSite().getId());
            item.setSite(site.get().getUrl());
            item.setSiteName(site.get().getName());
            item.setSnippet(getSnippet(resultSearch.get(i).getKey().getContent()));
            detailedStatisticsSearches[i-start]=item;
        }
        SearchResponce responce = new SearchResponce();
        responce.setResult(true);
        responce.setCount(countPage);
        responce.setData(detailedStatisticsSearches);
        return responce;
    }

    private DetailedStatisticsSearch[] getEmptyData() {
        DetailedStatisticsSearch[] result = new DetailedStatisticsSearch[1];
        DetailedStatisticsSearch item = new DetailedStatisticsSearch();
        item.setSnippet(" ");
        item.setTitle(" ");
        item.setSiteName(" ");
        item.setSite(" ");
        item.setRelevance(0);
        result[0]=item;
        return result;
    }

    private String getSnippet(String content) {
        Document document = Jsoup.parse(content);
        String body = document.body().text();
        List<String> formsNormal = getNormalFormsQeryLeters();
        String[] arrWords = body.split(" ");
        String[] lemmsFromtext = getLemsFromText(arrWords);
        ArrayList<Integer> indexConcidenceLemmAndWord = getIndexConcidence(lemmsFromtext, formsNormal);
        ArrayList<String> result = getMettLemsInText(arrWords, indexConcidenceLemmAndWord);

        return result.toString();
    }

    private ArrayList<String> getMettLemsInText(String[] arrWords, ArrayList<Integer> indexConcidenceLemmAndWord) {
        ArrayList<String> result = new ArrayList<>();
        int indexStart;
        int indexStop;
        int maxIndex = arrWords.length;
        for(Integer id: indexConcidenceLemmAndWord){
            indexStart = (id-15)>=0?(id-15):0;
            indexStop= (id+15)>=maxIndex?maxIndex:(id+15);
            String start = getStroke(arrWords,indexStart, id);
            String end = getStroke(arrWords,id+1, indexStop);
            result.add("<br>"+start +" "+"<b>" +arrWords[id]+"</b>"+" "+end);
        }
        return result;
    }

    private String getStroke(String[] arrWords, int indexStart, int indexStop) {
       String[] res=Arrays.copyOfRange(arrWords, indexStart, indexStop);
       StringBuilder sb = new StringBuilder();
       for(String s:res){
           sb.append(s);
           sb.append(" ");
       }
       return sb.toString();

    }

    private String[] getLemsFromText(String[] arrWords) {
        String[] lemmsFromtext = new String[arrWords.length];
        for(int i=0;i<arrWords.length;i++){
            try{
                arrWords[i] = arrWords[i].toLowerCase();
                String normalForm = lematizator.luceneMorph.getNormalForms(arrWords[i]).toString();
                lemmsFromtext[i] = normalForm;
            }
            catch (WrongCharaterException wce){

            }
        }
        return lemmsFromtext;
    }

    private ArrayList<Integer> getIndexConcidence(String[] lemmsFromtext, List<String> formsNormal) {
        ArrayList<Integer> indexConcidenceLemmAndWord = new ArrayList<>();
        for(int i=0; i< lemmsFromtext.length; i++){
            if(lemmsFromtext[i]!=null){
                for (int j=0; j<formsNormal.size(); j++){
                    if(lemmsFromtext[i].contains(formsNormal.get(j))){
                        indexConcidenceLemmAndWord.add(i);
                    }
                }

            }
        }
        indexConcidenceLemmAndWord = removeDuble(indexConcidenceLemmAndWord);
        return indexConcidenceLemmAndWord;
    }

    private ArrayList<Integer> removeDuble(ArrayList<Integer> indexConcidenceLemmAndWord) {
        ArrayList<Integer> result = new ArrayList<>();
        for(Integer i: indexConcidenceLemmAndWord){
            if (!result.contains(i)){
                result.add(i);
            }
        }
        return  result;
    }

    private List<String> getNormalFormsQeryLeters() {
        List<String> lemms = new ArrayList<>(search.getSetLemmQuery());
        List<List<String>> forms = (ArrayList)lemms.stream().map(l->lematizator.luceneMorph.getNormalForms(l)).collect(Collectors.toList());
        List<String> formsNormal = new ArrayList<>();
        for(List<String> list: forms){
            formsNormal.addAll(list);
        }
        return  formsNormal;
    }

    private String getTitle(String content) {
        Document document = Jsoup.parse(content);
        Element head = document.head();
        String title;
        try{
            title = head.select("head > title").first().text();
        }
        catch(NullPointerException npe){
            title = "not found title";
        }
        return title;
    }
    @Override
    public ResponseEntity getStatistics(String query, String site, String limit, String offset){
        this.setLimit(Integer.parseInt(limit));
        this.setSite(site);
        this.setQuery(query);
        this.setOffset(Integer.parseInt(offset));
        SearchResponce responce = this.getStat();
        return  new ResponseEntity(responce, HttpStatus.OK);
    }


}
