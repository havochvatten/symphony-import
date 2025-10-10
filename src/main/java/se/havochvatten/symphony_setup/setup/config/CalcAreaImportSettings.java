package se.havochvatten.symphony_setup.setup.config;

import org.apache.commons.cli.ParseException;
import org.geotools.api.data.SimpleFeatureReader;
import org.geotools.api.feature.Property;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.type.FeatureType;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import se.havochvatten.symphony_setup.setup.model.BaselineVersion;

import java.io.File;
import java.io.IOException;

public class CalcAreaImportSettings extends SettingsBase {
    public CalcAreaImportSettings(BaselineVersion baselineVersion, String inputFilePath, boolean clear) throws ParseException {
        super(baselineVersion, inputFilePath, "caF", "calculation area GeoPackage", clear);

        File gpkgFile = new File(inputFilePath);

        if (!gpkgFile.exists()) {
            throw new ParseException("some descriptive exception msg");
        }

        try (GeoPackage geoPackage = new GeoPackage(gpkgFile)) {
            SimpleFeatureReader featureReader;

            for (FeatureEntry f : geoPackage.features()) {
                featureReader = geoPackage.reader(f, null, null);

                while (featureReader.hasNext()) {
                    SimpleFeature feature = featureReader.next(); // throw if null
                    System.out.println(feature.getName());
                }
            }
        } catch (IOException e) {
            throw new ParseException("some descriptive exception msg");
        }
    }
}
