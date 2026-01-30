package se.havochvatten.symphony_setup.setup.config;
import se.havochvatten.symphony_setup.setup.model.BaselineVersion;

import java.util.Set;

public class CalcAreaImportSettings extends SettingsBase {

    public final boolean noDefaultArea() {
        return !allDefault && defaultAreaNames.isEmpty();
    }
    public final Set<String> defaultAreaNames;
    public final boolean allDefault;
    public final String nameProperty;

    public CalcAreaImportSettings(
        BaselineVersion baselineVersion,
        String inputFilePath,
        String nameProperty,
        boolean clear,
        boolean allDefault,
        String[] defaultAreaNames) {
        super(baselineVersion, inputFilePath, "caF", "calculation area GeoPackage", clear);

        this.defaultAreaNames = defaultAreaNames == null ? Set.of() : Set.of(defaultAreaNames);
        this.allDefault = allDefault;
        this.nameProperty = nameProperty;
    }
}
