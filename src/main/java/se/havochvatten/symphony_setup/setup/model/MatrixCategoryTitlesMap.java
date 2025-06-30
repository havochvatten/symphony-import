package se.havochvatten.symphony_setup.setup.model;

import java.util.*;

public class MatrixCategoryTitlesMap {
    private final Map<String, Integer> ecosystemTitleIdsMap = new HashMap<>();
    private final Map<String, Integer> pressureTitleIdsMap = new HashMap<>();
    
    public MatrixCategoryTitlesMap(List<String> ecoTitles, List<String> pressureTitles,
                                   Collection<SymphonyBand> ecoBands, Collection<SymphonyBand> pressureBands,
                                   String language) {

        for (SymphonyBand ecoBand : ecoBands) {
            ecosystemTitleIdsMap.put(ecoBand.getTitle(language), ecoBand.getId());
        }

        for (SymphonyBand pressureBand : pressureBands) {
            pressureTitleIdsMap.put(pressureBand.getTitle(language), pressureBand.getId());
        }
    }
    
    public Map<String, Integer> getTitleIdsMap(SymphonyCategory category) {
        return category == SymphonyCategory.ECOSYSTEM ?
            ecosystemTitleIdsMap :
            pressureTitleIdsMap;
    }
}
