package searchengine.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import searchengine.dto.responce.IndexingResponseOk;
import searchengine.dto.responce.UriForPost;
import searchengine.dto.search.SearchResponce;
import searchengine.lemmatization.LemmatizatorServiсeImpl;
import searchengine.services.IndexingServiceImpl;
import searchengine.wordsearchresponse.SearchStatisticImplPreparationResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiControllerTest {

    @Mock
    SearchStatisticImplPreparationResponse searchStatistic;
    @Mock
    LemmatizatorServiсeImpl lematizatorServise;

    @Mock
    IndexingServiceImpl indexingService;
    @InjectMocks
    ApiController apiController;

    @Test
    void indexingPage() {
        //given
        IndexingResponseOk irOk = new IndexingResponseOk();
        irOk.setResult(true);
        ResponseEntity responseEntity = new ResponseEntity<>(irOk, HttpStatus.OK);
        doReturn(responseEntity).when(lematizatorServise).getResponse("");
        //when
        UriForPost uri = new UriForPost();
        uri.setUrl("");
        ResponseEntity actual = this.apiController.indexingPagePost(uri);
        //then
        assertEquals(responseEntity, actual);
        verify(lematizatorServise).getResponse("");
    }

    @Test
    void search() {
        //given
        SearchResponce responce = new SearchResponce();
        responce.setResult(false);
        responce.setError("Поисковый запрос должен содержать только РУССКИЕ!!! буквы");
        ResponseEntity responseEntity = new ResponseEntity<>(responce, HttpStatus.OK);
        doReturn(responseEntity).when(searchStatistic).getStatistics("","","","");
        //when
        ResponseEntity actual = this.apiController.search("","","","");
        //then
        assertEquals(responseEntity, actual);
    }

    @Test
    void indexing() {
        //given
        IndexingResponseOk indexingResponseOk = new IndexingResponseOk();
        indexingResponseOk = new IndexingResponseOk();
        indexingResponseOk.setResult(true);
        ResponseEntity responseEntity = new ResponseEntity<>(indexingResponseOk, HttpStatus.OK);
        doReturn(responseEntity).when(indexingService).getStartResponse();
        //when
        ResponseEntity actual = apiController.indexing();
        //then
        assertEquals(responseEntity, actual);

    }
}