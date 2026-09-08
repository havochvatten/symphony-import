package se.havochvatten.symphonyconfig.setup.config;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.cli.ParseException;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Model class representing the structure of a JSON/YAML import configuration file.
 * Supports deserialization from both formats using Jackson.
 */
public class ImportConfigFile {

    /**
     * Supported import operations.
     */
    public enum Operation {
        NEW_BASELINE("newBaseline"),
        UPDATE("update"),
        NATIONAL_AREAS("nationalAreas");

        @JsonValue
        private final String value;
        
        Operation(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private Operation operation;
    private BaselineConfig baseline;
    private List<MetadataConfig> metadata;
    private List<MatrixConfig> matrices;
    private CalculationAreasConfig calculationAreas;
    private List<NationalAreaConfig> nationalAreas;
    private CsvSettingsConfig csvSettings;

    // Path to the config file itself (for relative path resolution). Deliberately has no
    // Jackson-visible getter/setter: it is derived at parse time, never user-supplied, and
    // a plain @JsonIgnore on the accessors would make Jackson treat "configFilePath" as a
    // known-but-ignorable key instead of rejecting it as unrecognized. Omitting any bean
    // accessor is what makes FAIL_ON_UNKNOWN_PROPERTIES actually reject it (see parse()
    // below, which assigns the field directly since it is a static method of this class).
    private transient Path configFilePath;

    private static final String BOUNDARY_TYPE = "BOUNDARY";

    public static class BaselineConfig {
        private Integer id;
        private String name;
        private String title;
        private String description;
        private String validFrom;
        private String locale;
        private String ecoPath;
        private String pressurePath;
        private SymphonySetup.UpdateMode updateMode;

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getValidFrom() { return validFrom; }
        public void setValidFrom(String validFrom) { this.validFrom = validFrom; }

        public String getLocale() { return locale; }
        public void setLocale(String locale) { this.locale = locale; }

        public String getEcoPath() { return ecoPath; }
        public void setEcoPath(String ecoPath) { this.ecoPath = ecoPath; }

        public String getPressurePath() { return pressurePath; }
        public void setPressurePath(String pressurePath) { this.pressurePath = pressurePath; }

        public SymphonySetup.UpdateMode getUpdateMode() { return updateMode; }
        public void setUpdateMode(SymphonySetup.UpdateMode updateMode) { this.updateMode = updateMode; }
    }

    public static class MetadataConfig {
        private String file;
        private String language;

        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }

    public static class MatrixConfig {
        private String file;
        private String name;
        private String language;

        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }

    public static class CalculationAreasConfig {
        private String file;
        private String nameProperty;
        private Boolean allDefault;
        private List<String> defaultAreas;

        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }

        public String getNameProperty() { return nameProperty; }
        public void setNameProperty(String nameProperty) { this.nameProperty = nameProperty; }

        public Boolean getAllDefault() { return allDefault; }
        public void setAllDefault(Boolean allDefault) { this.allDefault = allDefault; }

        public List<String> getDefaultAreas() { return defaultAreas; }
        public void setDefaultAreas(List<String> defaultAreas) { this.defaultAreas = defaultAreas; }
    }

    public static class NationalAreaConfig {
        private String type;
        private String file;
        private String countryISO;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }

        public String getCountryISO() { return countryISO; }
        public void setCountryISO(String countryISO) { this.countryISO = countryISO; }
    }

    public static class CsvSettingsConfig {
        private String delimiter;
        private String newline;

        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }

        public String getNewline() { return newline; }
        public void setNewline(String newline) { this.newline = newline; }
    }

    // Main class getters and setters
    public Operation getOperation() { return operation; }
    public void setOperation(Operation operation) { this.operation = operation; }

    public BaselineConfig getBaseline() { return baseline; }
    public void setBaseline(BaselineConfig baseline) { this.baseline = baseline; }

    public List<MetadataConfig> getMetadata() { return metadata; }
    public void setMetadata(List<MetadataConfig> metadata) { this.metadata = metadata; }

    public List<MatrixConfig> getMatrices() { return matrices; }
    public void setMatrices(List<MatrixConfig> matrices) { this.matrices = matrices; }

    public CalculationAreasConfig getCalculationAreas() { return calculationAreas; }
    public void setCalculationAreas(CalculationAreasConfig calculationAreas) {
        this.calculationAreas = calculationAreas;
    }

    public List<NationalAreaConfig> getNationalAreas() { return nationalAreas; }
    public void setNationalAreas(List<NationalAreaConfig> nationalAreas) {
        this.nationalAreas = nationalAreas;
    }

    public CsvSettingsConfig getCsvSettings() { return csvSettings; }
    public void setCsvSettings(CsvSettingsConfig csvSettings) { this.csvSettings = csvSettings; }

    /**
     * Parse a configuration file (JSON or YAML format).
     *
     * @param configFile Path to the configuration file
     * @return Parsed ImportConfigFile object
     * @throws IOException if file cannot be read or parsed
     */
    public static ImportConfigFile parse(String configFile) throws IOException {
        File file = new File(configFile);
        if (!file.exists()) {
            throw new IOException("Configuration file not found: " + configFile);
        }

        ObjectMapper mapper;
        if (configFile.toLowerCase().endsWith(".yaml") || configFile.toLowerCase().endsWith(".yml")) {
            mapper = new ObjectMapper(new YAMLFactory());
        } else if (configFile.toLowerCase().endsWith(".json")) {
            mapper = new ObjectMapper();
        } else {
            throw new IOException("Unsupported configuration file format. Use .json, .yaml, or .yml");
        }

        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);

        ImportConfigFile config;
        try {
            config = mapper.readValue(file, ImportConfigFile.class);
        } catch (InvalidFormatException e) {
            if (e.getTargetType() == Operation.class) {
                throw new IOException(String.format(
                    "Unknown operation type: '%s'. Must be one of: newBaseline, update, nationalAreas",
                    e.getValue()));
            }
            throw e;
        }

        config.configFilePath = file.toPath().toAbsolutePath().getParent();
        return config;
    }

    /**
     * Resolve a file path relative to the configuration file's directory.
     * If the path is already absolute, it is returned as-is.
     *
     * @param relativePath Path from the configuration file
     * @return Absolute path
     */
    public String resolvePath(String relativePath) {
        if (relativePath == null) {
            return null;
        }

        Path path = Paths.get(relativePath);

        // return absolute path as-is (normalized)
        if (path.isAbsolute()) {
            return path.normalize().toString();
        }

        // Resolve relative to input file directory
        if (configFilePath != null) {
            return configFilePath.resolve(relativePath).normalize().toString();
        }

        // Fall back to current directory (edge case)
        return path.toAbsolutePath().normalize().toString();
    }

    /**
     * Validate the entire configuration before any database write occurs.
     * Throws on the first problem found, naming the offending configuration path.
     */
    public void validate() throws ParseException {
        if (operation == null) {
            throw new ParseException(
                "Configuration must declare an 'operation'. "
                    + "One of: newBaseline, update, nationalAreas");
        }

        switch (operation) {
            case NEW_BASELINE   -> validateNewBaseline();
            case UPDATE         -> validateUpdate();
            case NATIONAL_AREAS -> validateNationalAreas();
        }

        validateCsvSettings();
    }

    private void validateNewBaseline() throws ParseException {
        rejectInapplicable("nationalAreas", nationalAreas != null);

        if (baseline == null || isBlank(baseline.getName())) {
            throw new ParseException("'baseline.name' is required for operation 'newBaseline'.");
        }
        if (baseline.getId() != null) {
            rejectInapplicable("baseline.id", true);
        }
        validateCommonSections();
    }

    private void validateUpdate() throws ParseException {
        rejectInapplicable("nationalAreas", nationalAreas != null);

        if (baseline == null || baseline.getId() == null) {
            throw new ParseException("'baseline.id' is required for operation 'update'.");
        }
        rejectInapplicable("baseline.name",         !isBlank(baseline.getName()));
        rejectInapplicable("baseline.ecoPath",      !isBlank(baseline.getEcoPath()));
        rejectInapplicable("baseline.pressurePath", !isBlank(baseline.getPressurePath()));

        if (metadata == null && matrices == null && calculationAreas == null) {
            throw new ParseException(
                "Operation 'update' requires at least one of 'metadata', 'matrices' "
                    + "or 'calculationAreas'.");
        }
        validateCommonSections();
    }

    private void validateCommonSections() throws ParseException {
        if (metadata != null) {
            for (int i = 0; i < metadata.size(); ++i) {
                requireReadableFile(metadata.get(i).getFile(), "metadata[" + i + "].file");
            }
        }
        if (matrices != null) {
            for (int i = 0; i < matrices.size(); ++i) {
                requireReadableFile(matrices.get(i).getFile(), "matrices[" + i + "].file");
                if (isBlank(matrices.get(i).getName())) {
                    throw new ParseException("'matrices[" + i + "].name' is required.");
                }
            }
        }
        if (calculationAreas != null) {
            requireReadableFile(calculationAreas.getFile(), "calculationAreas.file");

            boolean allDefault = Boolean.TRUE.equals(calculationAreas.getAllDefault());
            boolean namedDefaults = calculationAreas.getDefaultAreas() != null
                && !calculationAreas.getDefaultAreas().isEmpty();

            if (allDefault && namedDefaults) {
                throw new ParseException(
                    "'calculationAreas.allDefault' and 'calculationAreas.defaultAreas' are "
                        + "mutually exclusive. Provide one or the other.");
            }
        }
    }

    private void validateNationalAreas() throws ParseException {
        rejectInapplicable("baseline",         baseline != null);
        rejectInapplicable("metadata",         metadata != null);
        rejectInapplicable("matrices",         matrices != null);
        rejectInapplicable("calculationAreas", calculationAreas != null);
        rejectInapplicable("csvSettings",      csvSettings != null);

        if (nationalAreas == null || nationalAreas.isEmpty()) {
            throw new ParseException(
                "Configuration must include a 'nationalAreas' section for operation 'nationalAreas'.");
        }

        String firstIso = null;
        for (int i = 0; i < nationalAreas.size(); ++i) {
            NationalAreaConfig na = nationalAreas.get(i);

            if (isBlank(na.getType())) {
                throw new ParseException("'nationalAreas[" + i + "].type' is required.");
            }
            if (isBlank(na.getCountryISO())) {
                throw new ParseException("'nationalAreas[" + i + "].countryISO' is required.");
            }
            if (na.getCountryISO().trim().length() != 3) {
                throw new ParseException(String.format(
                    "'nationalAreas[%d].countryISO' must be an ISO 3166-1 alpha-3 code, e.g. 'SWE'. Got '%s'.",
                    i, na.getCountryISO()));
            }
            if (firstIso == null) {
                firstIso = na.getCountryISO();
            } else if (!firstIso.equals(na.getCountryISO())) {
                throw new ParseException(String.format(
                    "All entries in one national areas import must share the same 'countryISO'. "
                        + "Found both '%s' and '%s'. Import one country per configuration file.",
                    firstIso, na.getCountryISO()));
            }
            requireReadableFile(na.getFile(), "nationalAreas[" + i + "].file");
        }

        // Counted after the per-entry loop, so a missing 'type' is reported as such
        // rather than as a BOUNDARY entry that could not be found.
        long boundaries = nationalAreas.stream()
            .filter(na -> BOUNDARY_TYPE.equals(na.getType()))
            .count();

        if (boundaries != 1) {
            throw new ParseException(String.format(
                "A national areas import must contain exactly one BOUNDARY entry, found %d.",
                boundaries));
        }
    }

    private void validateCsvSettings() throws ParseException {
        if (csvSettings != null && csvSettings.getDelimiter() != null
                && csvSettings.getDelimiter().length() != 1) {
            throw new ParseException("'csvSettings.delimiter' must be a single character.");
        }
    }

    private void requireReadableFile(String configuredPath, String field) throws ParseException {
        if (isBlank(configuredPath)) {
            throw new ParseException("'" + field + "' is required.");
        }
        String resolved = resolvePath(configuredPath);
        if (!Files.isReadable(Paths.get(resolved))) {
            throw new ParseException(String.format(
                "'%s' refers to a file that does not exist or cannot be read: %s%n"
                    + "Paths are resolved relative to the configuration file's own directory.",
                field, resolved));
        }
    }

    private void rejectInapplicable(String section, boolean present) throws ParseException {
        if (present) {
            throw new ParseException(String.format(
                "Section '%s' is not applicable to operation '%s' and would be silently ignored. "
                    + "Remove it from the configuration file.",
                section, operation.getValue()));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
