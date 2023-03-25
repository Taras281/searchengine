package searchengine.lemmatization;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import searchengine.config.ApplicationContextHolder;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Component
public class SubLemmatizatorController {
    private ArrayDeque<String> dequeLinksForLematizator;
    private ExecutorService es;
    private ApplicationContext context;
    private LemmatizatorServiсeImpl lematizatorServise;

    SubLemmatizatorController(LemmatizatorServiсeImpl lematizatorServise){
        dequeLinksForLematizator = new ArrayDeque<>();
        context = ApplicationContextHolder.getApplicationContext();
        this.lematizatorServise = lematizatorServise;
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
