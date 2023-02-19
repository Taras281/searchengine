package searchengine.services;

import searchengine.model.Page;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class MyComparatorRolevant implements Comparator<Map.Entry<Page, float[]>> {



    @Override
    public int compare(Map.Entry<Page, float[]> o1, Map.Entry<Page, float[]> o2) {
        return (int)(o1.getValue()[0]-o2.getValue()[0]);
    }
}
