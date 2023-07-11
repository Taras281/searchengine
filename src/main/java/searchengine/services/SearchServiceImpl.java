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
import searchengine.tools.SearcherPage;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Data
public class SearchServiceImpl implements SearchService {
    private String  query;
    private String  site;
    private int  offset;
    private int  limit;
    @Autowired
    private SearcherPage searcherPage;
    @Autowired
    private Lemmatizator lemmatizator;
    @Autowired
    SiteRepository siteRepository;
    @Autowired
    Logger logger;
    private ArrayList<Map.Entry<Page, float[]>> resultSearch;
    private String oldQuery="";
    private String oldSite="";
    private int oldOffset=-1;
    private LocalDateTime labelTime=LocalDateTime.now();
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
        query = query.replaceAll("[^а-яА-ЯёЁ ]", " ").trim();
        if(query.equals("")){
            return getSimpleResponce(false, getEmptyData(), "Задан пустой поисковый запрос, или запрос содержит латинские или просто символы");
        }
        getPages(query);
        if(resultSearch==null){
            return getSimpleResponce(false, getEmptyData(), "результаты не найдены" );
        }
        if(resultSearch.get(0).getKey().getCode()==-1){
            return getSimpleResponce(false, getEmptyData(), "Слова образованные от- \"" +resultSearch.get(0).getKey().getPath() + "\" не найдены в базе, уберите их пожалуйста");
        }
        return getResponse(resultSearch);
    }

    private void getPages(String query) {
        searcherPage.setLimit(limit);
        searcherPage.setOffset(offset);
        searcherPage.setSite(site);
        if(!oldSite.equals(site)||!oldQuery.equals(query)||outdated()){
            resultSearch = searcherPage.getPage(query);
            oldQuery=query;
            oldSite=site;
        }
    }

    private boolean outdated() {
       if(LocalDateTime.now().isAfter(labelTime.plusSeconds(60))){
           labelTime=LocalDateTime.now();
           return true;
       }
       return false;
    }

    private SearchResponce getResponse(ArrayList<Map.Entry<Page, float[]>> resultSearch) {
        SearchResponce responce = new SearchResponce();
        responce.setResult(true);
        responce.setCount(resultSearch.size());
        responce.setData(getSnippets(resultSearch));
        return responce;
    }
    private SearchResponce getSimpleResponce(Boolean result, DetailedStatisticsSearch[] detailedStatisticsSearches, String error){
        SearchResponce responce = new SearchResponce();
        responce.setResult(result);
        responce.setData(detailedStatisticsSearches);
        responce.setError(error);
        return  responce;
    }
    private DetailedStatisticsSearch[] getSnippets(ArrayList<Map.Entry<Page,float[]>> resultSearch) {
        int start = offset>=resultSearch.size()?resultSearch.size()-1:offset;
        int dif = Math.abs(resultSearch.size()-start);
        int stop = Math.min(limit, dif);
        DetailedStatisticsSearch[] detailedStatisticsSearches = new DetailedStatisticsSearch[stop];
        for(int i = start; i<start+stop; i++){
            DetailedStatisticsSearch item = new DetailedStatisticsSearch();
            item.setRelevance(resultSearch.get(i).getValue()[0]);
            item.setUri(resultSearch.get(i).getKey().getPath());
            item.setTitle(getTitle(resultSearch.get(i).getKey().getContent()));
            Optional<Site> site = siteRepository.findById(resultSearch.get(i).getKey().getSite().getId());
            item.setSite(site.get().getUrl());
            item.setSiteName(site.get().getName());
            item.setSnippet(getSnippet(getText(resultSearch.get(i).getKey().getContent())));
            detailedStatisticsSearches[i-start]=item;
        }
        return detailedStatisticsSearches;
    }
    private String getText(String content) {
        String result = Jsoup.parse(content).body().text();
        return result;
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
        List<String> queryLemms = new ArrayList<>(searcherPage.getSetLemmQuery());
        ArrayList<String> sentences = getSentence(content);
        ArrayList<ArrayList<String>> sentenceByWord = getSentenceByWord(sentences);
        ArrayList<ArrayList<List<String>>> sentenceByLemm = getSentenceByLemm(sentenceByWord);
        HashMap<String, Integer> numberConcidienceSentenceQuery = getConcidentLemmInQueryAndSentence(queryLemms, sentences, sentenceByLemm);
        String resultSentence = getSentenceByMaxConcidence(numberConcidienceSentenceQuery);
        resultSentence = reduseSentence(resultSentence, queryLemms);
        String result = getSentenceAfterSelectionWord(resultSentence, queryLemms);
        return  result;
    }
    private String reduseSentence(String resultSentence, List<String> queryLemm) {
        List<String> sentenceByWord = getWords(resultSentence);
        if(sentenceByWord.size() < 31){
            return resultSentence;
        }
        List<List<String>> sentenceByLemm = getLemmsFromText(sentenceByWord);
        int indexMeet = getFirstMeetLemmaInSentence(sentenceByLemm, queryLemm);
        int[] startEnd = getStartEnd(indexMeet, sentenceByWord);
        StringBuffer stringBuffer = new StringBuffer();
        for(int i=startEnd[0]; i<startEnd[1];i++){
            stringBuffer.append(sentenceByWord.get(i));
            stringBuffer.append(" ");
        }
        return stringBuffer.toString();
    }
    private int getFirstMeetLemmaInSentence(List<List<String>> sentenceByLemm, List<String> queryLemm) {
        int indexConcat=-1;
        for(List<String> lemms: sentenceByLemm){
            indexConcat++;
            if(isAnyContains(lemms, queryLemm)) {
                break;
            }
        }
        return indexConcat;
    }
    private int[] getStartEnd(int indexMeet, List<String> sentenceByWord) {
        int start=0;
        int end=0;
        int lengthSnippet=30;
        if(indexMeet <= lengthSnippet){
            start = 1;
            end = lengthSnippet+1;
        } else if (indexMeet>=sentenceByWord.size()-lengthSnippet) {
            start = sentenceByWord.size()-lengthSnippet;
            end = sentenceByWord.size();
        }
        else{
            start = indexMeet-lengthSnippet/2;
            end = indexMeet+lengthSnippet/2;
        }
        return new int[]{start,end};
    }

    private String getSentenceAfterSelectionWord(String resultSentence, List<String> query){
        List<String> sentenceByWord = getWords(resultSentence);
        List<List<String>> sentenceByLemma = getLemmsFromText(sentenceByWord);
        StringBuffer sb = new StringBuffer();
        for(int lemmaId=0; lemmaId<sentenceByLemma.size();lemmaId++){
                if(isAnyContains(sentenceByLemma.get(lemmaId), query)){
                    sb.append("<b>");
                    sb.append(sentenceByWord.get(lemmaId));
                    sb.append("</b>");
                }
                else{
                    sb.append(sentenceByWord.get(lemmaId));
                }
                sb.append(" ");
        }
        return sb.toString();
    }

    private boolean isAnyContains(List<String> sentence, List<String> query) {
        for(String s:sentence){
            if (query.contains(s)){
            return true;}
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
        for (String sentence : sentences) {
            sentenceByWord.add(getWords(sentence));
        }
        return sentenceByWord;
    }
    private ArrayList<String> getWords(String sentence){
        ArrayList<String> words = new ArrayList<>(Arrays.asList(sentence.split("[«»“”\\p{Punct}\\s]+")));
        return words;
    }
    private ArrayList<ArrayList<List<String>>> getSentenceByLemm(ArrayList<ArrayList<String>> sentenceByWord ) {
        ArrayList<ArrayList<List<String>>> sentenceByLemm = new ArrayList<>();
        for (ArrayList<String> strings : sentenceByWord) {
            sentenceByLemm.add(getLemmsFromText(strings));
        }
        return sentenceByLemm;
    }

    private HashMap<String, Integer> getConcidentLemmInQueryAndSentence(List<String> queryLemms, ArrayList<String> sentences,
                                                                        ArrayList<ArrayList<List<String>>> sentenceByLemm) {
        HashMap<String, Integer> numberConcidienceSentenceQuery = new HashMap<>();
        for(int numSentence=0; numSentence<sentenceByLemm.size(); numSentence++){
            int count = 0;
            for(int numWord=0; numWord<sentenceByLemm.get(numSentence).size(); numWord++){
                count += (int) sentenceByLemm.get(numSentence).get(numWord).stream().filter(lemma->queryLemms.contains(lemma)).count();
            }
            numberConcidienceSentenceQuery.put(sentences.get(numSentence), count);
        }
        return numberConcidienceSentenceQuery;
    }

    private String getSentenceByMaxConcidence(HashMap<String, Integer> numberConcidenceSentenceByQuery){
        int maxFreq=0;
        String result="";
        for(Map.Entry<String, Integer> sentence: numberConcidenceSentenceByQuery.entrySet()){
           if(sentence.getValue()>maxFreq){
               maxFreq = sentence.getValue();
               result=sentence.getKey();
           }
        }
        return  result;
    }

    private ArrayList<List<String>> getLemmsFromText(List<String> arrWords) {
        ArrayList<List<String>> lemmsFromText = new ArrayList<>();
        for (String word : arrWords) {
            try {
                word = word.toLowerCase();
                if (word.length() < 2) {
                    lemmsFromText.add(Arrays.asList(word));
                    continue;
                }
                List<String> normalForm = lemmatizator.luceneMorph.getNormalForms(word);
                lemmsFromText.add(normalForm);
            } catch (WrongCharaterException wce) {
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
            title = "Not found title";
            logger.info(title + "  " + npe);
        }
        return title;
    }
}
