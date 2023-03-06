package searchengine.services.lemmatization;import org.apache.lucene.morphology.russian.RussianLuceneMorphology;import org.jsoup.Jsoup;import org.springframework.stereotype.Component;import java.io.IOException;import java.net.URISyntaxException;import java.util.ArrayList;import java.util.HashMap;import java.util.List;@Componentpublic class Lemmatizator {    private String content;    public Lemmatizator() {        try {            this.luceneMorph = new RussianLuceneMorphology();        } catch (IOException e) {            throw new RuntimeException(e);        }    }    public RussianLuceneMorphology luceneMorph;    public HashMap<String, Integer> getLems(String content) {        this.content = content;        try {            content = removeTag(content);        } catch (URISyntaxException e) {            throw new RuntimeException(e);        } catch (IOException e) {            throw new RuntimeException(e);        }        String[] words = getWordsFromText(content);        ArrayList<String> lems = (ArrayList<String>) getLems(words);        HashMap<String, Integer> countLems = counterWord(lems);        return  countLems;    }         public String[] getWordsFromText(String string){            String newString =string.replaceAll("[^а-яА-ЯёЁ ]", "").toLowerCase();            return newString.split(" ");        }         public List<String> getLems(String[] words){            List<String> result = new ArrayList<>();            for(String w:words){                if(!w.isEmpty()) {                    String l = luceneMorph.getMorphInfo(w).toString();                    String ls = luceneMorph.getNormalForms(w).toString();                    l = l.substring(0, l.indexOf("|"));                    l = l.replaceAll("[\\Q[\\E\\Q]\\E]", "");                    result.add(l);                }            }            return result;        }        private HashMap<String, Integer> counterWord(List<String> list){            HashMap<String, Integer> hm = new HashMap<>();            while (list.size()>0){                String word = list.get(0);                int counter=1;                for (int j=1; j<list.size();j++){                    String wordnext = list.get(j);                    if(word.equals(wordnext)){                        counter++;                    }                }            hm.put(word,counter);            while (list.contains(word)){                list.remove(word);            };      }         return hm;        }         public  String removeTag(String path) throws URISyntaxException, IOException {        String content = Jsoup.parse(path).text();        return   content;}   }