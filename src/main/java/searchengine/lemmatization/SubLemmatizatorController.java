package searchengine.lemmatization;

import org.springframework.context.ApplicationContext;
import searchengine.config.ApplicationContextHolder;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubLemmatizatorController {
    private static SubLemmatizatorController instance;
    private ArrayDeque<String> dequeLinksForLematizator;
    private ExecutorService es;
    ApplicationContext context;
    LemmatizatorServiсeImpl lematizatorServise;
    public static synchronized SubLemmatizatorController getInstance() {
        if (instance == null) {
            instance = new SubLemmatizatorController();
        }
        return instance;
    }
    SubLemmatizatorController(){
        dequeLinksForLematizator = new ArrayDeque<>(1000);
        context = ApplicationContextHolder.getApplicationContext();
        lematizatorServise = context.getBean(LemmatizatorServiсeImpl.class);
        es = Executors.newFixedThreadPool(1);
    }
    public void addDeque(String url){
        dequeLinksForLematizator.addLast(url);
        if(dequeLinksForLematizator.size()>=1){
        startLematization();
        }
    }

    private void startLematization() {
        es.execute(new Runnable() {
            @Override
            public void run() {
                while (!dequeLinksForLematizator.isEmpty()){
                    lematizatorServise.setPathParsingLink(dequeLinksForLematizator.removeFirst());
                    lematizatorServise.setRewritePage(false);
                    lematizatorServise.runing();
                }
            }
        });
    }
}
