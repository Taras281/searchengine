package searchengine.wordsearchresponse;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.lemmatization.LemmatizatorReturnCountWord;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Data
public class Searcher {
    private String  query;
    private String  site;
    private int  offset;
    private int  limit;
    private long siteId;
    @Autowired
    private LemmatizatorReturnCountWord lematizator;
    @Autowired
    private PageRepository pageReposytory;
    @Autowired
    private IndexRepository indexReposytory;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    LemmaRepository lemmaReposytory;
    @PersistenceContext
    private EntityManager entityManager;
    Set<String> setLemmQuery;
    public Set<String> getSetLemmQuery() {
        return setLemmQuery;
    }

    public  ArrayList<Map.Entry<Page, float[]>> vorker(String query){
       siteId = site.equals("-1")?-1:siteRepository.findByUrl(site).getId();
       setLemmQuery = getQeryLemma(query.trim());
       List<Lemma> listLemma = getLemmaFromBase(setLemmQuery);
       if (excessWordQery(setLemmQuery, listLemma)!=null){
           return excessWordQery(setLemmQuery, listLemma);
       }
       if(listLemma.size()<1){
           return null;
       }
       Collections.sort(listLemma, new MyComparator());
       List<Lemma> reduseList = reduseList(listLemma, 200);
       List<Page>  listPage = getPage(reduseList, siteId);
       if (listPage.size()<1){
           return null;
       }
       HashMap<Page, float[]> relevantion = getRelevantion(listPage, setLemmQuery);
       ArrayList<Map.Entry<Page, float[]>> relevantionSorted = sort(relevantion);
       return relevantionSorted;
    }

    private ArrayList<Map.Entry<Page, float[]>> excessWordQery(Set<String> setLemmQuery, List<Lemma> listLemma) {
            StringBuilder diferense = new StringBuilder();
            List<String> lems = listLemma.stream().map(lemma -> lemma.getLemma()).collect(Collectors.toList());
            for(String word: setLemmQuery){
                if(!lems.contains(word)){
                    diferense.append(word);
                    diferense.append(", ");
                }
            }
            if (diferense.toString().length()>1){
            ArrayList<Map.Entry<Page, float[]>> dontContain = new ArrayList<>();
            Page p = new Page();
            float[] f = new float[1];
            p.setPath(diferense.toString());
            p.setCode(-1);
            HashMap<Page, float[]> res = new HashMap<>();
            res.put(p, f);
            dontContain.add(res.entrySet().stream().findAny().get());
            return dontContain;
        }
        else return null;
    }


    private ArrayList<Map.Entry<Page, float[]>> sort(HashMap<Page, float[]> relevantion) {
        ArrayList<Map.Entry<Page, float[]>> result = new ArrayList<>();
        while (0<relevantion.size()){
            Float maxRel = relevantion.entrySet().stream().map(e->e.getValue()[0]).max(Float::compare).get();
            List<Map.Entry<Page, float[]>> entryMax= relevantion.entrySet().stream().filter(e->e.getValue()[0]==maxRel).collect(Collectors.toList());
            relevantion.remove(entryMax.get(0).getKey());
            result.add(entryMax.get(0));
        }
        return result;
    }

    private HashMap<Page,float[]> getRelevantion(List<Page> listPage, Set<String> listLemma) {
       /*для каждой страници получаем список индексов
         отфильтровываем индексы для лемм из запроса
         сумируем rank для лемм из запроса
         в результате получаем МАПУ содержащую страницу и соответствующую ей абсолютный и относительный rank*/

        List<Integer> rankAbsList = new ArrayList<>();
        HashMap<Page, float[]> result = new HashMap<>();
        for(Page page: listPage){
            List<Index> indexList = indexReposytory.findAllByPageId(page);
            int sumRankPage = (int)indexList.stream().filter(index -> listLemma.contains(index.getLemmaId().getLemma()))
                                                     .mapToDouble(index -> index.getRank()).sum();
            rankAbsList.add(sumRankPage);
        }
        Integer maxRank = rankAbsList.stream().max(Integer::compare).get();
        for(int i=0;i<listPage.size();i++){
            float rankRelativ = Float.valueOf(rankAbsList.get(i))/Float.valueOf(maxRank);
            result.put(listPage.get(i), new float[]{rankAbsList.get(i), rankRelativ});
        }
    return  result;
    }/**/

    private List<Page> getPage(List<Lemma> reduseList, long siteId) {
        /*
        по первой лемме получить индексы,
        по индексам получить список страниц
        0 для этого списка получить список индексов для всех страницах
          1 из полученного списка по следующей лемме (из списка лемм сформированного из запроса) получить страници
          2 из списка индексов 0 отфильтровать индексы соответствующие этим страницам
         */
        List<Index> indexFerstLemma = indexReposytory.findAllByLemmaId(reduseList.get(0));
        List<Page> listPageFerstLemma=new ArrayList<>();
        if (siteId!=-1){
            listPageFerstLemma = indexFerstLemma.stream().map(index -> index.getPageId()).
                    filter(page -> page.getSite().getId()==siteId).collect(Collectors.toList());
        }
        else{
            listPageFerstLemma = indexFerstLemma.stream().map(index -> index.getPageId()).collect(Collectors.toList());
        }
        List<Index> listAllIndexPages = indexReposytory.findAllByPageIdIn(listPageFerstLemma);
        reduseList.remove(0);
        for(Lemma lemma:reduseList){
            List<Page> pagesNextLemma = listAllIndexPages.stream().filter(index->index.getLemmaId().getId()==lemma.getId()).map(l->l.getPageId()).collect(Collectors.toList());
            listAllIndexPages=listAllIndexPages.stream().filter(index -> pagesNextLemma.contains(index.getPageId())).collect(Collectors.toList());
            if(listAllIndexPages.size()==0){
                return new ArrayList<Page>();
            }
        }
        List<Page> pages =  listAllIndexPages.stream().map(index -> index.getPageId()).collect(Collectors.toList());
        pages=removeDoblePage(pages);
        return  pages;
    }

    private List<Page> removeDoblePage(List<Page> pages) {
        List<Page> result = new ArrayList<>();
        for(Page page:pages){
            if(!result.contains(page)){
                result.add(page);
            }
        }
        return result;
    }

    private List<Lemma> reduseList(List<Lemma> listLemma, int procentLemm) {
        if (listLemma.size()<2){
            return listLemma;
        }
        int n = listLemma.size();
        int summfreq = listLemma.stream().mapToInt(l->l.getFrequency()).sum();
        double average = summfreq/n;
        double summAverageMinusfreqLemma = listLemma.stream().mapToDouble(l-> Math.pow((average - l.getFrequency()),2)).sum();
        double deviation = Math.pow(summAverageMinusfreqLemma/(n-1), 0.5d);
        int treshold = (int)(average + deviation*(Double.valueOf(procentLemm)/100d));
        List<Lemma> result = listLemma.stream().filter(lemma -> lemma.getFrequency()<=treshold).collect(Collectors.toList());
        return result;
    }

    public Set<String> getQeryLemma(String query){
        query=query.replaceAll("ё","е");
        String[] arr = lematizator.getWordsFromText(query);
        Set<String> list= new HashSet<>();
        for(String s: arr){
            String st = lematizator.luceneMorph.getMorphInfo(s).toString();
            List<String> normalForms = lematizator.luceneMorph.getNormalForms(s);
            String[] array = st.split("\\|", 2);
            if(array[0].substring(1).length()<2){
                continue;
            }
            if(array[1].contains(" С ")||array[1].contains(" Г ")||array[1].contains(" П ")||
                    array[1].contains(" ПРИЧАСТИЕ ")||array[1].contains("КР_ПРИЧАСТИЕ")||
                    array[1].contains("ИНФИНИТИВ")||array[1].contains("КР_ПРИЛ")||array[1].contains("ПРЕДК")){
               list.addAll(normalForms);
            }
        }
        list = removeDoble(list);
        return list;
    }



    public List<Lemma> getLemmaFromBase(Set<String> listQueryWord){
        List<Lemma> ll = lemmaReposytory.findAllByLemmaIn(listQueryWord);
        return ll;
    }

    private Set<String> removeDoble(Set<String> list) {
        Set<String> result=new HashSet<>();
        for(String str:list){
            if (!result.contains(str)){
                result.add(str);
            }
        }
        return result;
    }

    private class MyComparator implements Comparator<Lemma> {
        @Override
        public int compare(Lemma o1, Lemma o2) {
            return o1.getFrequency()-o2.getFrequency();
        }
    }

}
