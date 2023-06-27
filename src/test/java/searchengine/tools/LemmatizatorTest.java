package searchengine.tools;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class LemmatizatorTest {
    Lemmatizator lemmatizator = new Lemmatizator();
    String[] input1= new String[]{"дом","вчатв"};
    List<String> output = Arrays.asList("дом",  "вчатва");

    @Test
    void getLemms() {
        Assert.assertEquals(output, lemmatizator.getLemss(input1));
    }
}