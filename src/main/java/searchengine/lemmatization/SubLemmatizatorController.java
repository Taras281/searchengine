package searchengine.lemmatization;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import searchengine.config.ApplicationContextHolder;
import searchengine.model.Page;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
@Component
public class SubLemmatizatorController {
    private ArrayDeque<Page> dequeLinksForLematizator;
    private ExecutorService es;
    private ApplicationContext context;
    private LemmatizatorServiсeImpl lematizatorServise;

    SubLemmatizatorController(LemmatizatorServiсeImpl lematizatorServise){
        dequeLinksForLematizator = new ArrayDeque<>();
        context = ApplicationContextHolder.getApplicationContext();
        this.lematizatorServise = lematizatorServise;
        int countProcessor = Runtime.getRuntime().availableProcessors();
        es = Executors.newFixedThreadPool(1);
    }

    public void addDeque(Page page){
        dequeLinksForLematizator.addLast(page);
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
