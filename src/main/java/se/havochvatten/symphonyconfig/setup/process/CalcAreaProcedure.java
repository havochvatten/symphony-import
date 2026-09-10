package se.havochvatten.symphonyconfig.setup.process;

import org.apache.commons.cli.ParseException;
import org.geotools.api.data.SimpleFeatureReader;
import org.geotools.api.feature.Property;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.Geometry;
import se.havochvatten.symphonyconfig.setup.config.CalcAreaImportSettings;
import se.havochvatten.symphonyconfig.setup.model.CalculationArea;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class CalcAreaProcedure extends ImportProcedure<CalcAreaImportSettings> {

    private static final String DEFAULT_MX_PROPERTY = "matrixName";
    private static final String ADDITIONAL_MX_PROPERTY = "addMatrices";
    private static final String AREA_TYPE_PROPERTY = "areaType";

    public record AreaMatrixTuple(CalculationArea area, List<Integer> matrixIds){}

    public AreaMatrixTuple[] areaTuples;

    @Override
    protected String getImportItemName() {
        return String.join(", ",
            Arrays.stream(areaTuples)
                  .map(t -> t.area.getAreaName())
                  .toArray(String[]::new));
    }

    public CalcAreaProcedure(CalcAreaImportSettings settings) throws ParseException {
        super(settings);
        process();
    }

    @Override
    public boolean validate() {
        if (!new File(settings.inputFilePath).exists()) {
            validationErrors.add("The specified calculation area GeoPackage file was not found on the given path.");
            return false;
        }

        return true;
    }

    @Override
    public boolean collect() {
        Set<Integer> invalidAreaTypes = new HashSet<>();
        Set<String> invalidMatrixNames = new HashSet<>();
        List<AreaMatrixTuple> calcAreasList = new ArrayList<>();

        try (GeoPackage geoPackage = new GeoPackage(new File(settings.inputFilePath))) {
            SimpleFeatureReader featureReader;

            for (FeatureEntry f : geoPackage.features()) {
                featureReader = geoPackage.reader(f, null, null);
                CalculationArea calculationArea;
                List<Integer> additionalMatrixIds = new ArrayList<>();

                while (featureReader.hasNext()) {
                    SimpleFeature feature = featureReader.next();

                    Property areaNameProperty = feature.getProperty(settings.nameProperty);
                    Property sensMatrixNameProperty = feature.getProperty(DEFAULT_MX_PROPERTY);

                    if (areaNameProperty != null &&
                        sensMatrixNameProperty != null &&
                        areaNameProperty.getValue() != null &&
                        sensMatrixNameProperty.getValue() != null) {
                        String areaName = areaNameProperty.getValue().toString();
                        calculationArea = new CalculationArea(
                                areaName,
                                sensMatrixNameProperty.getValue().toString(),
                                (Geometry) feature.getDefaultGeometry(),
                                settings.allDefault || settings.defaultAreaNames.contains(areaName)
                        );
                    } else {
                        throw new RuntimeException(
                            String.format(
                                "Area name property ('%s') or matrix name property ('%s') " +
                                "missing on polygon feature",
                                settings.nameProperty, DEFAULT_MX_PROPERTY));
                    }

                    Property areaTypeProperty = feature.getProperty(AREA_TYPE_PROPERTY);
                    Property additionalMatricesProperty = feature.getProperty(ADDITIONAL_MX_PROPERTY);

                    if (areaTypeProperty != null && areaTypeProperty.getValue() != null) {
                        Integer areaType = Integer.parseInt(areaTypeProperty.getValue().toString());
                        if (settings.availableAreaTypes.contains(areaType)){
                            calculationArea.setAreaType(areaType);
                        } else {
                            invalidAreaTypes.add(areaType);
                        }
                    }

                    if (additionalMatricesProperty != null && additionalMatricesProperty.getValue() !=  null) {
                        List<String> additionalMatrixNames =
                            Arrays.stream(additionalMatricesProperty.getValue().toString().split(","))
                                  .map(String::trim).toList();
                        for (String matrixName : additionalMatrixNames) {
                            if (settings.matrixNamesMap.containsKey(matrixName)) {
                                additionalMatrixIds.add(settings.matrixNamesMap.get(matrixName));
                            } else {
                                invalidMatrixNames.add(matrixName);
                            }
                        }
                    }

                    calcAreasList.add(new AreaMatrixTuple(calculationArea, additionalMatrixIds));
                }

                featureReader.close();
            }

            areaTuples  = calcAreasList.toArray(new AreaMatrixTuple[calcAreasList.size()]);

        } catch (IOException e) {
            validationErrors.add("The specified calculation area file is not a valid GeoPackage.");
        } catch (RuntimeException e) {
            validationErrors.add("Error during calculation area import procedure: " + e.getMessage());
        }

        if (!invalidAreaTypes.isEmpty()) {
            validationMessages.add(String.format("The following area type ids are not present in the db: %s",
                String.join(", ", invalidAreaTypes.stream().map(String::valueOf).toList())));
        }

        return validationErrors.isEmpty();
    }

    public boolean confirmImport() {
        String notice = settings.noDefaultArea() ?
                "Notice: No default calculation area specified. This may be a valid condition when importing partial data." :
                "";

        notice += !validationMessages.isEmpty() ?
            validationMessages.stream().map(
                msg -> String.format("%nNotice: %s", msg)).collect(Collectors.joining()) :
                "";

        return confirmPendingImport(notice.isEmpty() ? null : notice);
    }
}
