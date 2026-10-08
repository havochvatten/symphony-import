package se.havochvatten.symphonyconfig.setup.config;
import se.havochvatten.symphonyconfig.setup.model.BaselineVersion;

import java.util.Map;
import java.util.Set;

public class CalcAreaImportSettings extends SettingsBase {

    /** Decimals kept on polygon coordinates. 7 is about 1 cm, well below a baseline raster cell. */
    public static final int DEFAULT_DECIMALS = 7, MIN_DECIMALS = 1, MAX_DECIMALS = 15;

    public final boolean noDefaultArea() {
        return !allDefault && defaultAreaNames.isEmpty();
    }
    public final Set<String> defaultAreaNames;
    public final boolean allDefault;
    public final String nameProperty;
    public final Set<Integer> availableAreaTypes;
    public final Map<String, Integer> matrixNamesMap;

    public CalcAreaImportSettings(
        BaselineVersion baselineVersion,
        String inputFilePath,
        String nameProperty,
        boolean allDefault,
        String[] defaultAreaNames,
        Set<Integer> availableAreaTypes,
        Map<String, Integer> matrixNamesMap) {
        super(baselineVersion, inputFilePath, "caF", "calculation area GeoPackage");

        this.availableAreaTypes = availableAreaTypes;
        this.matrixNamesMap = matrixNamesMap;
        this.defaultAreaNames = defaultAreaNames == null ? Set.of() : Set.of(defaultAreaNames);
        this.allDefault = allDefault;
        this.nameProperty = nameProperty;
    }
}
