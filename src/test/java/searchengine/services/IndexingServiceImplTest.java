package searchengine.services;

import org.apiguardian.api.API;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import searchengine.config.Label;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.responce.IndexingResponseNotOk;
import searchengine.dto.responce.IndexingResponseOk;
import searchengine.reposytory.SiteRepository;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class IndexingServiceImplTest {


    @Mock
    SiteRepository siteRepository;
    @InjectMocks
    IndexingServiceImpl indexingService;
    @Mock
    SitesList sitesList;
    @Mock
    Label label;
    @Test
    void getStartResponse_IndexingNotStarted() {
        //given
        setStatusIndexing(false);
        List<Site> list = new ArrayList<>();
        list.add(new Site("url1", "site1"));
        list.add(new Site("url2", "site2"));
        doReturn(list).when(sitesList).getSites();
        IndexingResponseOk indexingResponseOk= new IndexingResponseOk();
        indexingResponseOk.setResult(true);
        ResponseEntity responseEntity = new ResponseEntity(indexingResponseOk, HttpStatus.OK);
        //when
        ResponseEntity actualResponse = indexingService.getStartResponse();
        //then
        assertEquals(responseEntity, actualResponse);
    }

    @Test
    void getStartResponse_IndexingStarted() {
        //given
        setStatusIndexing(true);
        List<Site> list = new ArrayList<>();
        list.add(new Site("url1", "site1"));
        list.add(new Site("url2", "site2"));
        IndexingResponseNotOk indexingResponseNotOk= new IndexingResponseNotOk();
        indexingResponseNotOk.setResult(false);
        indexingResponseNotOk.setError("");
        ResponseEntity responseEntity = new ResponseEntity(indexingResponseNotOk, HttpStatus.BAD_REQUEST);
        //when
        ResponseEntity actualResponse = indexingService.getStartResponse();
        //then
        assertEquals(responseEntity.getStatusCode(), actualResponse.getStatusCode());
    }



    private void setStatusIndexing(boolean b) {
        Class ClassIndexingServiceImpl;
        Field indexingStateField;
        try {
            ClassIndexingServiceImpl = indexingService.getClass();
            indexingStateField = ClassIndexingServiceImpl.getDeclaredField("indexingState");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        Boolean var = false;
        try {
            indexingStateField.setAccessible(true);
            indexingStateField.setBoolean(indexingService, b);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


}