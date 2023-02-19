package searchengine.configure;

import org.apache.logging.log4j.LogManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Logger {
    @Bean
    public org.apache.logging.log4j.Logger getLog(){
        return LogManager.getLogger();
        //return LoggerFactory.getLogger(Logger.class);
    }

}
