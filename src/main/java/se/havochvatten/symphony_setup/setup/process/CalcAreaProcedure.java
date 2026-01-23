package se.havochvatten.symphony_setup.setup.process;

import org.apache.commons.cli.ParseException;
import org.geotools.api.data.SimpleFeatureReader;
import org.geotools.api.feature.Property;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.Geometry;
import se.havochvatten.symphony_setup.setup.config.CalcAreaImportSettings;
import se.havochvatten.symphony_setup.setup.model.CalculationArea;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class CalcAreaProcedure extends ImportProcedure<CalcAreaImportSettings> {

    public CalculationArea[] areas;

    @Override
    protected String getImportItemName() {
        return String.join(", ",
            Arrays.stream(areas)
                  .map(CalculationArea::getAreaName)
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
        List<CalculationArea> calcAreasList = new ArrayList<>();

        try (GeoPackage geoPackage = new GeoPackage(new File(settings.inputFilePath))) {
            SimpleFeatureReader featureReader;

            for (FeatureEntry f : geoPackage.features()) {
                featureReader = geoPackage.reader(f, null, null);

                while (featureReader.hasNext()) {
                    SimpleFeature feature = featureReader.next();

                    Property areaNameProperty = feature.getProperty(settings.nameProperty);
                    Property sensMatrixNameProperty = feature.getProperty("matrixName");
                    if (areaNameProperty.getValue() != null &&  sensMatrixNameProperty.getValue() != null) {
                        String areaName = areaNameProperty.getValue().toString();
                        calcAreasList.add(
                            new CalculationArea(
                                areaName,
                                sensMatrixNameProperty.getValue().toString(),
                                (Geometry) feature.getDefaultGeometry(),
                                settings.allDefault || settings.defaultAreaNames.contains(areaName)
                            ));
                    } else {
                        throw new RuntimeException(
                            String.format(
                                "Area name property ('%s') or matrix name property ('matrixName') " +
                                "missing on polygon feature", settings.nameProperty));
                    }
                }
                featureReader.close();
            }
            areas  = calcAreasList.toArray(new CalculationArea[calcAreasList.size()]);

        } catch (IOException e) {
            validationErrors.add("The specified calculation area file is not a valid GeoPackage.");
        } catch (RuntimeException e) {
            validationErrors.add("Error during calculation area import procedure: " + e.getMessage());
        }

        return validationErrors.isEmpty();
    }

    public boolean confirmImport() {
        Scanner prompt = pendingImportMessage(
            settings.noDefaultArea() ?
                "Notice: No default calculation area specified. This may be a valid condition when importing partial data." :
                null);

        return prompt.nextLine().trim().equalsIgnoreCase("y");
    }
}
