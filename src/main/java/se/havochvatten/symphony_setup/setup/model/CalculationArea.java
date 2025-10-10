package se.havochvatten.symphony_setup.setup.model;

import org.geotools.api.feature.Feature;

public class CalculationArea {
    private final Feature geometricFeature;

    public CalculationArea(Feature geometricFeature, boolean defaultArea, int defaultMatrixId) {
        this.geometricFeature = geometricFeature;
    }
}
