package searchengine.services;

import lombok.Data;
import org.apache.lucene.morphology.WrongCharaterException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.dto.search.DetailedStatisticsSearch;
import searchengine.dto.search.SearchResponce;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.SiteRepository;
import searchengine.tools.LemmatizatorReturnCountWord;
import searchengine.tools.Searcher;
import java.util.*;

@Service
@Data
public class SearchServiceImpl implements SearchService {
    private String  query;
    private String  site;
    private int  offset;
    private int  limit;
    @Autowired
    private Searcher search;
    @Autowired
    private LemmatizatorReturnCountWord lemmatizator;
    @Autowired
    SiteRepository siteRepository;
    @Autowired
    Logger logger;
    @Override
    public ResponseEntity getStatistics(String query, String site, String limit, String offset){
        this.setLimit(Integer.parseInt(limit));
        this.setSite(site);
        this.setQuery(query);
        this.setOffset(Integer.parseInt(offset));
        SearchResponce responce = this.getStatistics();
        return  new ResponseEntity(responce, HttpStatus.OK);
    }

    public SearchResponce getStatistics() {
        query =  query.replaceAll("[a-zA-Z]", " ");
        query =  query.replaceAll("[^а-яА-ЯёЁ]", " ");
        if(query.matches("[ ]*?")){
            return getSimpleResponse(false, 0, getEmptyData(), "Задан пустой поисковый запрос, или в запросе содержатся только латинские или просто  символы");
        }
        search.setLimit(limit);
        search.setOffset(offset);
        search.setSite(site);
        ArrayList<Map.Entry<Page, float[]>> resultSearch = search.vorker(query);
        if(resultSearch==null){
            return getSimpleResponse(false, 0, getEmptyData(),"результаты не найдены");
        }
        if(resultSearch.get(0).getKey().getCode()==-1){
            return getSimpleResponse(false, 0,getEmptyData(), "Слова - \"" +resultSearch.get(0).getKey().getPath() + "\" не найдены в базе, уберите их пожалуйста" );
        }
        SearchResponce responce = getResponse(resultSearch);
        return responce;
    }

    private SearchResponce getResponse(ArrayList<Map.Entry<Page, float[]>> resultSearch) {
        SearchResponce responce = new SearchResponce();
        int countPage = resultSearch.size();
        int start = offset>=resultSearch.size()?resultSearch.size()-1:offset;
        int dif = Math.abs(resultSearch.size()-start);
        int stop = limit>dif?dif:limit;
        DetailedStatisticsSearch[] detailedStatisticsSearches = new DetailedStatisticsSearch[stop];
        for(int i = start; i<start+stop; i++){
            DetailedStatisticsSearch item = new DetailedStatisticsSearch();
            item.setRelevance(resultSearch.get(i).getValue()[0]);
            item.setUri(resultSearch.get(i).getKey().getPath());
            item.setTitle(getTitle(resultSearch.get(i).getKey().getContent()));
            Optional<Site> site = siteRepository.findById(resultSearch.get(i).getKey().getSite().getId());
            item.setSite(site.get().getUrl());
            item.setSiteName(site.get().getName());
            item.setSnippet(getSnippet(resultSearch.get(i).getKey().getContent()));
            detailedStatisticsSearches[i-start]=item;
        }
        return getSimpleResponse(true, countPage, detailedStatisticsSearches, "");
    }
    private SearchResponce getSimpleResponse(Boolean result, int countPage, DetailedStatisticsSearch[] detailedStatisticsSearch, String error){
        SearchResponce responce = new SearchResponce();
        responce.setResult(result);
        responce.setCount(countPage);
        responce.setData(detailedStatisticsSearch);
        responce.setError(error);
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
        item.setUri(" ");
        result[0]=item;
        return result;
    }

    private String getSnippet(String content){
        List<String> queryLemms = new ArrayList<>(search.getSetLemmQuery());
        ArrayList<String> sentences = getSentence(content);
        ArrayList<ArrayList<String>> sentenceByWord = getSentenceByWord(sentences);
        ArrayList<ArrayList<List<String>>> sentenceByLemm = getSentenceByLemm(sentenceByWord);
        HashMap<Integer, String> numberConcidienceSentenceQuery = getConcidient(queryLemms, sentences, sentenceByLemm);
        int maxFreq = numberConcidienceSentenceQuery.keySet().stream().max(Integer::compareTo).get();
        String resultSentence = numberConcidienceSentenceQuery.get(maxFreq);
        String result = getSelection(resultSentence, query);
        return  result;
    }

    private String getSelection(String resultSentence, String query) {
        ArrayList<String> sentenceByWord = getWords(resultSentence);
        Set<String> queryByLemm= search.getSetLemmQuery();
        StringBuilder sentence = new StringBuilder();
        int start = 0;
        int counter= 0;
        int lengthSnippet = 30;
        for(String word: sentenceByWord){
            counter++;
            if(start!=0&&counter>lengthSnippet){
                break;
            }
            String smallLetterWord = word.replaceAll("[^а-яА-ЯёЁ ]", " ").toLowerCase().trim();
            List<String> lemms = lemmatizator.getLemms(new String[]{smallLetterWord});
            if(isContain(queryByLemm,lemms)){
                if(counter>lengthSnippet/2&&start==0){
                    counter=0;
                    sentence = new StringBuilder();
                }
                start=counter;
                sentence.append("<b>");
                sentence.append(word);
                sentence.append("</b> ");
            }
            else{
                sentence.append(word+" ");
            }
            }
        String result = sentence.toString();
        return result;

    }

    private boolean isContain(Set<String> queryByLemm, List<String> lemms) {
        for(String queryLemma: queryByLemm){
            if(lemms.contains(queryLemma)){
                return true;
            }
        }
        return false;
    }


    private ArrayList<String> getSentence(String content) {
        String text=Jsoup.parse(content).text();
        text=text.replaceAll("Ё", "Е");
        text=text.replaceAll("ё", "е");
        ArrayList<String> sentences =new ArrayList<>(Arrays.asList(text.split("[.!?]\\s*")));
        return sentences;

    }

    private ArrayList<ArrayList<String>> getSentenceByWord(ArrayList<String> sentences) {
        ArrayList<ArrayList<String>> sentenceByWord = new ArrayList<>();
        for(int i=0;i<sentences.size();i++){
            sentenceByWord.add(getWords(sentences.get(i)));
        }
        return sentenceByWord;
    }
    private ArrayList<String> getWords(String sentence){
        ArrayList<String> words = new ArrayList<>(Arrays.asList(sentence.split("[«»“”\\p{Punct}\\s]+")));
        return words;
    }
    private ArrayList<ArrayList<List<String>>> getSentenceByLemm(ArrayList<ArrayList<String>> sentenceByWord ) {
        ArrayList<ArrayList<List<String>>> sentenceByLemm = new ArrayList<>();
        for(int i=0;i<sentenceByWord.size();i++){
            sentenceByLemm.add(getLemmsFromText(sentenceByWord.get(i)));
        }
        return sentenceByLemm;
    }

    private HashMap<Integer, String> getConcidient(List<String> qeryLemms, ArrayList<String> sentences, ArrayList<ArrayList<List<String>>> sentenceByLemm) {
        HashMap<Integer, String> numberConcidienceSentenceQuery = new HashMap<>();
    for(int numSentence=0; numSentence<sentenceByLemm.size(); numSentence++){
        int count =0;
        for( int numWord=0; numWord<sentenceByLemm.get(numSentence).size(); numWord++){
            for(int numLemm=0; numLemm<sentenceByLemm.get(numSentence).get(numWord).size();numLemm++){
                if(qeryLemms.contains(sentenceByLemm.get(numSentence).get(numWord).get(numLemm))){
                    ++count;
                }
            }
        }
        numberConcidienceSentenceQuery.put(count, sentences.get(numSentence));
    }
    return numberConcidienceSentenceQuery;
    }

    private ArrayList<List<String>> getLemmsFromText(ArrayList<String> arrWords) {
        ArrayList<List<String>> lemmsFromText = new ArrayList<>();
        for(int i=0;i<arrWords.size();i++){
            String word = arrWords.get(i);
            try{
                word = word.toLowerCase();
                if(word.length()<2){
                    lemmsFromText.add(Arrays.asList(word));
                    continue;
                }
                List<String> normalForm = lemmatizator.luceneMorph.getNormalForms(word);
                lemmsFromText.add(normalForm);
            }
            catch (WrongCharaterException wce){
                lemmsFromText.add(Arrays.asList(word));
                logger.error(wce.toString());
            }
        }
        return lemmsFromText;
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
            logger.info(title + "  " + npe);
        }
        return title;
    }

}
