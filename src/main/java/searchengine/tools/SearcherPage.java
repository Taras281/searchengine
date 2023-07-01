package searchengine.tools;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Data
public class SearcherPage {
    private String  query;
    private String  site;
    private int  offset;
    private int  limit;
    private long siteId;
    @Autowired
    private Lemmatizator lemmatizator;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    Set<String> setLemmQuery;
    public Set<String> getSetLemmQuery() {
        return setLemmQuery;
    }

    public  ArrayList<Map.Entry<Page, float[]>> getPage(String query){
       siteId = site.equals("-1")?-1:siteRepository.findByUrl(site).getId();
       setLemmQuery = getQueryLemma(query.trim());
       List<Lemma> listLemmaFromBase = getLemmaFromBase(setLemmQuery);
       if (missingWordQueryInBase(setLemmQuery, listLemmaFromBase)!=null){
           return missingWordQueryInBase(setLemmQuery, listLemmaFromBase);
       }
       if(listLemmaFromBase.size()<1){
           return null;
       }
       listLemmaFromBase.sort(new MyComparator());
       List<Lemma> reduseList = reduseList(listLemmaFromBase, 2);
       List<Page>  listPage = getPage(reduseList, siteId);
       if (listPage.size()<1){
           return null;
       }
       HashMap<Page, float[]> relevantion = getRelevantion(listPage, setLemmQuery);
       ArrayList<Map.Entry<Page, float[]>> relevantionSorted = sortRelevantion(relevantion);
       return relevantionSorted;
    }

    private ArrayList<Map.Entry<Page, float[]>> missingWordQueryInBase(Set<String> setLemmQuery, List<Lemma> listLemmaFromBase) {
            StringBuilder diferense = new StringBuilder();
            List<String> lemmsFromBase = listLemmaFromBase.stream().map(lemma -> lemma.getLemma()).collect(Collectors.toList());
            for(String wordFromQuery: setLemmQuery){
                if(!lemmsFromBase.contains(wordFromQuery)){
                    diferense.append(wordFromQuery);
                    diferense.append(", ");
                }
            }
            if (diferense.toString().length()>1){
            ArrayList<Map.Entry<Page, float[]>> result = new ArrayList<>();
            Page page = new Page();
            float[] rolevant = new float[1];
            page.setPath(diferense.toString());
            page.setCode(-1);
            HashMap<Page, float[]> res = new HashMap<>();
            res.put(page, rolevant);
            result.add(res.entrySet().stream().findAny().get());
            return result;
        }
        else return null;
    }
    private ArrayList<Map.Entry<Page, float[]>> sortRelevantion(HashMap<Page, float[]> relevantion) {
        ArrayList<Map.Entry<Page, float[]>> result = new ArrayList<>();
        while (relevantion.size()>0){
            Float maxRel = relevantion.entrySet().stream().map(e->e.getValue()[0]).max(Float::compare).get();
            List<Map.Entry<Page, float[]>> entryMax= relevantion.entrySet().stream().filter(e->e.getValue()[0]==maxRel).collect(Collectors.toList());
            relevantion.remove(entryMax.get(0).getKey());
            result.add(entryMax.get(0));
        }
        return result;
    }

    private HashMap<Page,float[]> getRelevantion(List<Page> listPage, Set<String> listLemma) {
        List<Integer> rankAbsList = new ArrayList<>();
        HashMap<Page, float[]> result = new HashMap<>();
        for(Page page: listPage){
            List<Index> indexList = indexRepository.findAllByPageId(page);
            int sumRankPage = (int)indexList.stream().filter(index -> listLemma.contains(index.getLemmaId().getLemma()))
                                                     .mapToDouble(index -> index.getRank()).sum();
            rankAbsList.add(sumRankPage);
        }
        int maxRank = rankAbsList.stream().max(Integer::compare).get();
        for(int i=0;i<listPage.size();i++){
            float rankRelativ = Float.valueOf(rankAbsList.get(i))/Float.valueOf(maxRank);
            result.put(listPage.get(i), new float[]{rankAbsList.get(i), rankRelativ});
        }
    return  result;

    }
    private List<Page> getPage(List<Lemma> reduseList, long siteId) {
        List<Index> indexFirstLemma = indexRepository.findAllByLemmaId(reduseList.get(0));
        List<Page> listPageFirstLemma = new ArrayList<>();
        if (siteId!=-1){
            listPageFirstLemma = indexFirstLemma.stream().map(index -> index.getPageId()).
                    filter(page -> page.getSite().getId()==siteId).collect(Collectors.toList());
        }
        else{
            listPageFirstLemma = indexFirstLemma.stream().map(index -> index.getPageId()).collect(Collectors.toList());
        }
        List<Index> listAllIndexPages = indexRepository.findAllByPageIdIn(listPageFirstLemma);
        reduseList.remove(0);
        for(Lemma lemma:reduseList){
            List<Page> pagesNextLemma = listAllIndexPages.stream().filter(index->index.getLemmaId().getId()==lemma.getId()).map(l->l.getPageId()).collect(Collectors.toList());
            listAllIndexPages=listAllIndexPages.stream().filter(index -> pagesNextLemma.contains(index.getPageId())).collect(Collectors.toList());
            if(listAllIndexPages.size()==0){
                return new ArrayList<Page>();
            }
        }
        List<Page> pages =  listAllIndexPages.stream().map(index -> index.getPageId()).collect(Collectors.toList());
        pages = removeDoublePage(pages);
        return  pages;
    }

    private List<Page> removeDoublePage(List<Page> pages) {
        List<Page> result = new ArrayList<>();
        for(Page page:pages){
            if(!result.contains(page)){
                result.add(page);
            }
        }
        return result;
    }

    private List<Lemma> reduseList(List<Lemma> listLemma, int coefLemm) {
        if (listLemma.size()<2){
            return listLemma;
        }
        int n = listLemma.size();
        int summFreq = listLemma.stream().mapToInt(l->l.getFrequency()).sum();
        double average = summFreq/n;
        double summAverageMinusFreqLemma = listLemma.stream().mapToDouble(l-> Math.pow((average - l.getFrequency()),2)).sum();
        double deviation = Math.pow(summAverageMinusFreqLemma/(n-1), 0.5d);
        int treshold = (int)(average + deviation*(Double.valueOf(coefLemm)));
        List<Lemma> result = listLemma.stream().filter(lemma -> lemma.getFrequency()<=treshold).collect(Collectors.toList());
        return result;
    }

    public Set<String> getQueryLemma(String query){
        query=query.replaceAll("ё","е");
        String[] arr = lemmatizator.getWordsFromText(query);
        HashMap<String,String>  normalForms = new HashMap<>();
        for(String s: arr){
            List<String> res = lemmatizator.luceneMorph.getMorphInfo(s);
            HashMap<String, String> wordType = getWordType(res);
            normalForms.putAll(getLemmAfterFiltr(wordType));
        }
        Set<String> list = new HashSet<>(normalForms.keySet());
        return list;
    }

    private HashMap<String, String>  getLemmAfterFiltr(HashMap<String, String> wordType) {
        HashMap<String,String> result = new HashMap<>();
        for(Map.Entry<String, String> entry: wordType.entrySet()){
            if(filterTypeWord(entry.getValue())){
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private boolean filterTypeWord(String value) {
        return value.contains("С") || value.contains("Г") || value.contains("П") ||
                value.contains("ПРИЧАСТИЕ") || value.contains("КР_ПРИЧАСТИЕ") ||
                value.contains("ИНФИНИТИВ") || value.contains("КР_ПРИЛ") || value.contains("ПРЕДК");
    }

    private HashMap<String, String> getWordType(List<String> res) {
        HashMap<String, String> wordType= new HashMap<>();
        for(String lemmaInfo: res){
            String[] wordAndInfo = lemmaInfo.split("\\|");
            String typeWord = wordAndInfo[1].split(" ")[1];
            wordType.put(wordAndInfo[0],typeWord);
        }
        return wordType;
    }

    public List<Lemma> getLemmaFromBase(Set<String> listQueryWord){
        List<Lemma> listLemma = lemmaRepository.findAllByLemmaIn(listQueryWord);
        return listLemma;
    }

    private class MyComparator implements Comparator<Lemma> {
        @Override
        public int compare(Lemma o1, Lemma o2) {
            return o1.getFrequency()-o2.getFrequency();
        }
    }

}
