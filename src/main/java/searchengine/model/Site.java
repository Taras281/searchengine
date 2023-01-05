package searchengine.model;

import com.sun.istack.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private int id;

    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private myEnum status;

    @Column(name = "status_time", columnDefinition = "DATE", nullable = false)
    private Date statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT", nullable = false)
    private String lastError;

    @Column(name = "url", columnDefinition = "VARCHAR(255)", nullable = false)
    private String url;

    @Column(name = "name", columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;




}

enum myEnum{INDEXING, INDEXED, FAILED };