package searchengine.tools;import org.apache.lucene.morphology.russian.RussianLuceneMorphology;import org.jsoup.Jsoup;import org.jsoup.nodes.Document;import org.slf4j.Logger;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.stereotype.Component;import java.io.IOException;import java.util.ArrayList;import java.util.HashMap;import java.util.List;import java.util.stream.Collectors;@Componentpublic class LemmatizatorReturnCountWord {    @Autowired    Logger logger;    public RussianLuceneMorphology luceneMorph;    public LemmatizatorReturnCountWord(){        try {            this.luceneMorph = new RussianLuceneMorphology();        } catch (IOException e) {            logger.error("Error create RussianLuceneMorphology " + e);        }    }    public HashMap<String, Integer> getLems(String content) {        Document cont = Jsoup.parse(content);        content=cont.text();        content=content.replaceAll("ё", "е");        content=content.replaceAll("Ё", "Е");        String[] words = getWordsFromText(content);        ArrayList<String> lemms = (ArrayList<String>) getlemss(words);        HashMap<String, Integer> countLemms = counterWord(lemms);        return  countLemms;    }    public String[] getWordsFromText(String string){            String newString = string.replaceAll("[^а-яА-ЯёЁ ]", " ").toLowerCase().trim();            return newString.split(" +");    }    public List<String> getlemss(String[] words){            List<String> result = new ArrayList<>();            for(String w:words){                if(!w.isEmpty()) {                    List<String> lemms = luceneMorph.getNormalForms(w);                    lemms = lemms.stream().map(lemma->lemma.replaceAll("[\\Q[\\E\\Q]\\E]", "")).collect(Collectors.toList());                    result.addAll(lemms);                }            }            return result;    }     private HashMap<String, Integer> counterWord(List<String> list){            HashMap<String, Integer> hm = new HashMap<>();            while (list.size()>0){                String word = list.get(0);                int counter=1;                for (int j=1; j<list.size();j++){                    String wordNext = list.get(j);                    if(word.equals(wordNext)){                        counter++;                    }                }                hm.put(word,counter);                while (list.contains(word)){                       list.remove(word);                };      }         return hm;    } }