package searchengine.wordsearchresponse;

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
import searchengine.lemmatization.LemmatizatorReturnCountWord;
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
    private LemmatizatorReturnCountWord lematizator;
    @Autowired
    SiteRepository siteRepository;

    @Autowired
    Logger logger;

    public SearchResponce getStatistics() {
        if(query.equals("")){
            SearchResponce responce = new SearchResponce();
            responce.setResult(false);
            responce.setError("Задан пустой поисковый запрос");
            return responce;
        }
        if(query.matches(".*[a-zA-Z]+.*")){
            SearchResponce responce = new SearchResponce();
            responce.setResult(false);
            responce.setError("Поисковый запрос не должен содержать латинские буквы и символы");
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
        if(resultSearch.get(0).getKey().getCode()==-1){
            SearchResponce responce = new SearchResponce();
            responce.setResult(false);
            responce.setData(getEmptyData());
            responce.setError("Слова - \"" +resultSearch.get(0).getKey().getPath() + "\" не найдены в базе, уберите их пожалуйста");
            return responce;
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

    private String getSnippet(String content) {
        Document document = Jsoup.parse(content);
        String body = document.body().text();
        body=body.replaceAll("Ё", "Е");
        body=body.replaceAll("ё", "е");
        List<String> formsNormal = getNormalFormsQeryLeters();
        ArrayList<String> arrWords = separated(body);
        ArrayList<List<String>> lemmsFromtext = getLemsFromText(arrWords);
        ArrayList<Integer> indexConcidenceLemmAndWord = getIndexConcidence(lemmsFromtext, formsNormal);
        ArrayList<String> result = getMettLemsInText(arrWords, indexConcidenceLemmAndWord);
        return result.toString();
    }
    private String getSmallSnipet(String content){
        // разбиваем текст страници на пределожения
        //делаем список списков лемм каждого предложения
        // ищем предложение с максимальным совпадением колличества лем из запрлоса
        List<String> qeryLemms = new ArrayList<>(search.getSetLemmQuery());
        ArrayList<String> sentences = getSentence(content);
        ArrayList<ArrayList<String>> sentenceByWord = getSentenceByWord(sentences);
        ArrayList<ArrayList<List<String>>> sentenceByLemm = getSentenceByLemm(sentenceByWord);
        HashMap<Integer, String> numberConcidienceSentenceQery = getConcidient(qeryLemms, sentences, sentenceByLemm);
        int maxFreq = numberConcidienceSentenceQery.keySet().stream().max(Integer::compareTo).get();
        String resultSentence = numberConcidienceSentenceQery.get(maxFreq);
        String result = getSelection(resultSentence, query);
        return  result;
    }

    private String getSelection(String resultSentence, String query) {
        ArrayList<String> sentenceByWord = getWords(resultSentence);
        Set<String> queryByLemm= search.getSetLemmQuery();
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
            List<String> lemms =lematizator.getLems(new String[]{smalLeterWord});
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
            sentenceByLemm.add(getLemsFromText(sentenceByWord.get(i)));
        }
        return sentenceByLemm;
    }

    private HashMap<Integer, String> getConcidient(List<String> qeryLemms, ArrayList<String> sentences, ArrayList<ArrayList<List<String>>> sentenceByLemm) {
        HashMap<Integer, String> numberConcidienceSentenceQery = new HashMap<>();
    for(int numSentence=0; numSentence<sentenceByLemm.size(); numSentence++){
        int count =0;
        for( int numWord=0; numWord<sentenceByLemm.get(numSentence).size(); numWord++){
            for(int numLemm=0; numLemm<sentenceByLemm.get(numSentence).get(numWord).size();numLemm++){
                if(qeryLemms.contains(sentenceByLemm.get(numSentence).get(numWord).get(numLemm))){
                    ++count;
                }
            }
        }
        numberConcidienceSentenceQery.put(count, sentences.get(numSentence));
    }
    return numberConcidienceSentenceQery;
    }

    private  ArrayList<String> separated(String input) {
        int start = 0;
        int stop = 0;
        ArrayList<String> result = new ArrayList();
        String reg = "[\u0410-\u044F\u0451\u0401]";
        for (int i = 0; i < input.length()-1; i++) {
            if (input.substring(i, i + 1).matches(reg)) {
                continue;
            }
            stop = i;
            String leter1 = input.substring(start, stop);
            String leter2 = input.substring(stop, stop + 1);
            if (leter1 != "") {
                result.add(input.substring(start, stop));
            }
            if (leter2 != "") {
                result.add(input.substring(stop, stop + 1));
            }
            start = stop + 1;
        }
        return  result;
    }

    private ArrayList<String> getMettLemsInText(ArrayList<String> arrWords, ArrayList<Integer> indexConcidenceLemmAndWord) {
        ArrayList<String> result = new ArrayList<>();
        int ofset = 30;
        int indexStart;
        int indexStop;
        int maxIndex = arrWords.size();
        for(Integer id: indexConcidenceLemmAndWord){
            indexStart = (id-ofset)>=0?(id-ofset):0;
            indexStop= (id+ofset)>=maxIndex?maxIndex:(id+ofset);
            String start = getLine(arrWords,indexStart, id);
            String end = getLine(arrWords,id+1, indexStop);
            result.add("<br>"+start +" "+"<b>" +arrWords.get(id)+"</b>"+" "+end);
        }
        return result;
    }

    private String getLine(ArrayList<String> arrWords, int indexStart, int indexStop) {
       ArrayList<String> res= new ArrayList<>();
       for(int i = indexStart; i<indexStop;i++){
           res.add(arrWords.get(i));
       }
       StringBuilder sb = new StringBuilder();
       for(String s:res){
           sb.append(s);
       }
       return sb.toString();
    }

    private ArrayList<List<String>> getLemsFromText(ArrayList<String> arrWords) {
        ArrayList<List<String>> lemmsFromtext = new ArrayList<>();
        for(int i=0;i<arrWords.size();i++){
            String word = arrWords.get(i);
            try{
                word = word.toLowerCase();
                if(word.length()<2){
                    lemmsFromtext.add(Arrays.asList(word));
                    continue;
                }
                List<String> normalForm = lematizator.luceneMorph.getNormalForms(word);
                lemmsFromtext.add(normalForm);
            }
            catch (WrongCharaterException wce){
                lemmsFromtext.add(Arrays.asList(word));
                logger.error(wce.toString());
            }
        }
        return lemmsFromtext;
    }

    private ArrayList<Integer> getIndexConcidence(ArrayList<List<String>> lemmsFromtext, List<String> formsNormal) {
        ArrayList<Integer> indexConcidenceLemmInText = new ArrayList<>();
        for(int i=0; i< lemmsFromtext.size(); i++){
            if(lemmsFromtext.get(i)!=null){
                for (int j=0; j<formsNormal.size(); j++){
                    if(arrayContains(lemmsFromtext.get(i), formsNormal.get(j))){
                        indexConcidenceLemmInText.add(i);
                    }
                }
            }
        }
        indexConcidenceLemmInText = removeDuble(indexConcidenceLemmInText);
        return indexConcidenceLemmInText;
    }


    private boolean arrayContains(List<String> lemms, String normalForms) {
        for(String lemma: lemms){
            if(lemma.equals(normalForms)){
                return true;
            }
        }
        return  false;
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
            logger.info(title + "  " + npe);
        }
        return title;
    }
    @Override
    public ResponseEntity getStatistics(String query, String site, String limit, String offset){
        this.setLimit(Integer.parseInt(limit));
        this.setSite(site);
        this.setQuery(query);
        this.setOffset(Integer.parseInt(offset));
        SearchResponce responce = this.getStatistics();
        return  new ResponseEntity(responce, HttpStatus.OK);
    }
}
