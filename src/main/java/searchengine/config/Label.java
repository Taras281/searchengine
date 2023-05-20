package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "my-labels")
public class Label {
    private String indexingStarted;
    private String indexingNotStarted;
    private String indexingStopedUser;
    private String thisPageOutSite;
    private String checkPage;
}
