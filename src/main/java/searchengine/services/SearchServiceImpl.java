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
import searchengine.tools.Lemmatizator;
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
    private Searcher searcher;
    @Autowired
    private Lemmatizator lemmatizator;
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
        query = query.replaceAll("[^а-яА-ЯёЁ ]", "").trim();
        if(query.equals("")){
            return getSimpleResponce(false, getEmptyData(), "Задан пустой поисковый запрос, или запрос содержит латинские или просто символы");
        }
        searcher.setLimit(limit);
        searcher.setOffset(offset);
        searcher.setSite(site);
        ArrayList<Map.Entry<Page, float[]>> resultSearch = searcher.vorker(query);
        if(resultSearch==null){
            return getSimpleResponce(false, getEmptyData(), "результаты не найдены" );
        }
        if(resultSearch.get(0).getKey().getCode()==-1){
            return getSimpleResponce(false, getEmptyData(), "Слова - \"" +resultSearch.get(0).getKey().getPath() + "\" не найдены в базе, уберите их пожалуйста");
        }
        SearchResponce responce = getResponse(resultSearch);
        return responce;
    }
    private SearchResponce getSimpleResponce(Boolean result, DetailedStatisticsSearch[] detailedStatisticsSearches,
                                             String error){
        SearchResponce responce = new SearchResponce();
        responce.setResult(result);
        responce.setData(detailedStatisticsSearches);
        responce.setError(error);
        return  responce;
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
            item.setSnippet(getSmallSnipet(resultSearch.get(i).getKey().getContent()));
            detailedStatisticsSearches[i-start]=item;
        }
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
        item.setUri(" ");
        result[0]=item;
        return result;
    }

    private String getSmallSnipet(String content){
        List<String> queryLemms = new ArrayList<>(searcher.getSetLemmQuery());
        ArrayList<String> sentences = getSentence(content);
        ArrayList<ArrayList<String>> sentenceByWord = getSentenceByWord(sentences);
        ArrayList<ArrayList<List<String>>> sentenceByLemm = getSentenceByLemm(sentenceByWord);
        HashMap<String, Integer> numberConcidienceSentenceQuery = getConcidient(queryLemms, sentences, sentenceByLemm);
        String resultSentence = getSentenceByMaxConcidience(numberConcidienceSentenceQuery);
        String result = getSelection(resultSentence, queryLemms);
        return  result;
    }

    private String getSelection(String resultSentence, List<String> query){
        List<String> sentenceByWord = getWords(resultSentence);
        List<List<String>> sentenceByLemma = getLemsFromText(sentenceByWord);
        StringBuffer sb = new StringBuffer();
        for(int wordId=0; wordId<sentenceByLemma.size();wordId++){
                if(query.containsAll(sentenceByLemma.get(wordId))){
                    sb.append("<b>");
                    sb.append(sentenceByWord.get(wordId));
                    sb.append("</b>");
                }
                else{
                    sb.append(sentenceByWord.get(wordId));
                }
                sb.append(" ");
        }
        return sb.toString();
    }
/*    private String getSelection(String resultSentence, String query) {
        ArrayList<String> sentenceByWord = getWords(resultSentence);
        Set<String> queryByLemm= searcher.getSetLemmQuery();
        StringBuilder sentence = new StringBuilder();
        int start = 0;
        int counter= 0;
        int lengthSnipet = 30;
        for(String word: sentenceByWord){
            counter++;
            if(start!=0&&counter>lengthSnipet){
                break;
            }
            String smalLeterWord = word.replaceAll("[^а-яА-ЯёЁ ]", " ").toLowerCase().trim();
            List<String> lemms = lemmatizator.getlemss(new String[]{smalLeterWord});
            if(isContain(queryByLemm,lemms)){
                if(counter>lengthSnipet/2&&start==0){
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
            }}
        String result = sentence.toString();
        return result;

    }*/

    private boolean isContain(Set<String> queryByLemm, List<String> lemms) {
        for(String queryLemma: queryByLemm){
            if(lemms.contains(queryLemma)){
                return true;
            }}
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
            sentenceByLemm.add(getLemsFromText(sentenceByWord.get(i)));
        }
        return sentenceByLemm;
    }

    private HashMap<String, Integer> getConcidient(List<String> queryLemms, ArrayList<String> sentences, ArrayList<ArrayList<List<String>>> sentenceByLemm) {
        HashMap<String, Integer> numberConcidienceSentenceQuery = new HashMap<>();
        for(int numSentence=0; numSentence<sentenceByLemm.size(); numSentence++){
            int count = 0;
            for( int numWord=0; numWord<sentenceByLemm.get(numSentence).size(); numWord++){
                count += (int) sentenceByLemm.get(numSentence).get(numWord).stream().filter(lemma->queryLemms.contains(lemma)).count();
            }
            numberConcidienceSentenceQuery.put( sentences.get(numSentence), count);
        }
        return numberConcidienceSentenceQuery;
    }

    private String getSentenceByMaxConcidience(HashMap<String, Integer> numberConcidienceSentenceQuery){
        int maxFreq=0;
        String result="";
        for(Map.Entry<String, Integer> sentence: numberConcidienceSentenceQuery.entrySet()){
           if(sentence.getValue()>maxFreq){
               maxFreq = sentence.getValue();
               result=sentence.getKey();
           }
        }
        return  result;
    }

    private ArrayList<List<String>> getLemsFromText(List<String> arrWords) {
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
