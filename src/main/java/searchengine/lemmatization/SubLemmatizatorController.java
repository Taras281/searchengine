package searchengine.lemmatization;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import searchengine.config.ApplicationContextHolder;
import searchengine.model.Page;

import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class SubLemmatizatorController {
    private ArrayDeque<Page> dequeLinksForLematizator;
    private ExecutorService es;
    private ApplicationContext context;
    private LemmatizatorServiсeImpl lematizatorServise;
    int countProcessor;

    SubLemmatizatorController(LemmatizatorServiсeImpl lematizatorServise){
        dequeLinksForLematizator = new ArrayDeque<>();
        context = ApplicationContextHolder.getApplicationContext();
        this.lematizatorServise = lematizatorServise;
        es = Executors.newFixedThreadPool(1);
    }

    public void addDeque(Page page){
        dequeLinksForLematizator.addLast(page);
        if(dequeLinksForLematizator.size()>=1){
        startLematization();
        }
    }

    private void startLematization() {
        if (es.isTerminated()){
            es = Executors.newFixedThreadPool(1);
        }
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

    public void shutdown() {
        dequeLinksForLematizator.clear();
        es.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!es.awaitTermination(5, TimeUnit.SECONDS)) {
                es.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!es.awaitTermination(10, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            es.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
}
