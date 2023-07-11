package searchengine.services;

import org.junit.Before;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import searchengine.config.SitesList;
import searchengine.config.StatusEnum;
import searchengine.dto.response.IndexingResponseNotOk;
import searchengine.dto.response.IndexingResponseOk;
import searchengine.model.Site;
import searchengine.repository.SiteRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexingServiceImplTest {
    @Mock
    SiteRepository siteRepository;
    @Mock
    searchengine.model.Site site;
    @Mock
    SitesList sitesList;
    @Mock
    ForkJoinPool forkJoinPool;

    @InjectMocks
    IndexingServiceImpl indexingService;

    @Test
    void getStartResponse() {
        IndexingResponseOk indexingResponseOk =  new IndexingResponseOk();
        indexingResponseOk.setResult(true);
        when(siteRepository.findByStatus(StatusEnum.INDEXING)).thenReturn(new ArrayList<>());
        assertEquals(HttpStatus.OK, indexingService.getStartResponse().getStatusCode());
    }
    @Test
    void getStartResponseNotOk() {
        IndexingResponseNotOk indexingResponseNotOk = new IndexingResponseNotOk();
        indexingResponseNotOk.setResult(false);
        indexingResponseNotOk.setError("Индексация уже запущена");
        when(siteRepository.findByStatus(StatusEnum.INDEXING)).thenReturn(new ArrayList<>(Collections.singleton(site)));
        assertEquals(HttpStatus.OK, indexingService.getStartResponse().getStatusCode());
    }


}