package searchengine.services.wordsearchresponse;

import lombok.Data;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.reposytory.IndexRepository;
import searchengine.reposytory.LemmaRepository;
import searchengine.reposytory.PageRepository;
import searchengine.reposytory.SiteRepository;
import searchengine.services.lemmatization.Lemmatizator;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@Component
@Data
public class Searcher {
    private String  query;
    private String  site;
    private int  offset;
    private int  limit;
    private long siteId;
    @Autowired
    private Lemmatizator lematizator;

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

    public Set<String> getSetLemmQuery() {
        return setLemmQuery;
    }

    Set<String> setLemmQuery;
    public  ArrayList<Map.Entry<Page, float[]>> vorker(String query){
       siteId = site.equals("-1")?-1:siteRepository.findByUrl(site).getId();
       setLemmQuery = getQeryLemma(query.trim());
       List<Lemma> listLemma = getLemmaFromBase(setLemmQuery);
       if(listLemma.size()<1){
           return null;
       }
       Collections.sort(listLemma, new MyComparator());
       List<Lemma> reduseList = reduseList(listLemma, 200);
       List<Page>  listPage = getPage(reduseList);
       if (listPage.size()<1){
           return null;
       }
       HashMap<Page, float[]> relevantion = getRelevantion(listPage);
       ArrayList<Map.Entry<Page, float[]>> relevantionSorted = sort(relevantion);
       return relevantionSorted;

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

    private HashMap<Page,float[]> getRelevantion(List<Page> listPage) {
        List<Integer> rankAbsList = new ArrayList<>();
        HashMap<Page, float[]> result = new HashMap<>();
        for(Page page: listPage){
            List<Index> indexList = indexReposytory.findAllByPageId(page);
            int sumRankPage = (int)indexList.stream().mapToDouble(index -> index.getRank()).sum();
            rankAbsList.add(sumRankPage);

        }
        Integer maxRank = rankAbsList.stream().max(Integer::compare).get();
        for(int i=0;i<listPage.size();i++){
            float rankRelativ = Float.valueOf(rankAbsList.get(i))/Float.valueOf(maxRank);
            result.put(listPage.get(i), new float[]{rankAbsList.get(i), rankRelativ});
        }
    return  result;
    }

    private List<Page> getPage(List<Lemma> reduseList) {
        if(reduseList.size()<1){
            return new ArrayList<Page>();
        }
        // получить список индексов где есть лемма
        // взять индекс следующей леммы и выбрать из списка индексов те где она есть
        // повторять предыдущее действие пока список лемм не закончится или список индексов не будет равен 0
        List<Index> listIndex;
        listIndex=indexReposytory.findAllByLemmaId(reduseList.get(0));

        for (int i=1; i<reduseList.size(); i++){
            listIndex = findListLemma(listIndex,  reduseList.get(i));
            if(listIndex.size()==0){
                return new ArrayList<Page>();
            }
        }
        List<Page> pageList = listIndex.stream().map(index -> index.getPageId()).collect(Collectors.toList());
        if (siteId!=-1){
            pageList = pageList.stream().filter(page -> page.getSite().getId()==siteId).collect(Collectors.toList());
        }
        return pageList;
    }

    private List<Index> findListLemma(List<Index> listIndex, Lemma lemma1) {
         // по списку индексов найти все индексы лемм
        // по полученным леммам найти список индексов
        // из полученного списка выбрать содержащие индекс следующей леммы
         List<Lemma> lemmaList = listIndex.stream().map(index -> index.getLemmaId()).collect(Collectors.toList());
         List<Integer> listIdLemm = lemmaList.stream().map(lemma -> lemma.getId()).collect(Collectors.toList());
         listIdLemm = removeDoble(listIdLemm);
         indexReposytory.flush();
         List<Index> indexList1 = getLems(listIdLemm);
         List<Long> listPageId = indexList1.stream().map(index -> index.getPageId().getId()).collect(Collectors.toList());
         List<Index> indexList = indexReposytory.getAllByPageId(listPageId);
         return indexList.stream().filter(index -> index.getLemmaId().getId()==lemma1.getId()).collect(Collectors.toList());
    }

    private List<Integer> removeDoble(List<Integer> listIdLemm) {
        List<Integer> res = new ArrayList<>();
        for(int i: listIdLemm){
            if(!res.contains(i)){
                res.add(i);
            }
        }
        return res;
    }

    private List<Index> getLems(List<Integer> listIdLemm) {
        Session session = entityManager.unwrap(Session.class);
        List<Index> indexs = new ArrayList<>();
        Iterator iterator = listIdLemm.iterator();
        while (iterator.hasNext()) {
            String id = iterator.next().toString();
            String lemma1 = "FROM Index WHERE lemma_id = :lem";
            //  String lemma1 = "FROM Lemma WHERE id=11258";
            Query query = session.createQuery(lemma1);
            query.setParameter("lem", id);
            indexs.addAll(query.list());

        }
        return indexs;
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
        String[] arr = lematizator.getWordsFromText(query);
        Set<String> list= new HashSet<>();
        for(String s: arr){
            String st = lematizator.luceneMorph.getMorphInfo(s).toString();
            String[] array = st.split("\\|", 2);
            if(array[1].contains(" С ")||array[1].contains(" Г ")||array[1].contains(" П ")||
                    array[1].contains(" ПРИЧАСТИЕ ")||array[1].contains("КР_ПРИЧАСТИЕ")||
                    array[1].contains("ИНФИНИТИВ")||array[1].contains("КР_ПРИЛ")||array[1].contains("ПРЕДК")){
               list.add(array[0].substring(1));
            }
        }
        list = removeDoble(list);
        return list;
    }

    public List<Lemma> getLemmaFromBase(Set<String> listQueryWord){
        List<Lemma> ll = lemmaReposytory.getAllContainsLems(listQueryWord);
        if(!site.equals("-1")){
            ll = ll.stream().filter(lemma -> lemma.getSite().getId()==siteId).collect(Collectors.toList());
        }
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
