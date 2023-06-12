package searchengine.dto.statistics;

import lombok.Data;
import searchengine.config.StatusEnum;

import java.time.LocalDateTime;

@Data
public class DetailedStatisticsItem {
    private String url;
    private String name;
    private StatusEnum status;
    private LocalDateTime statusTime;
    private String error;
    private int pages;
    private int lemmas;
}
