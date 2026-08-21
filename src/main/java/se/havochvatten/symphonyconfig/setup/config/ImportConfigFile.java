package se.havochvatten.symphonyconfig.setup.config;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;

import java.io.File;
import java.io.IOException;
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

    // Path to the config file itself (for relative path resolution)
    private transient Path configFilePath;

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

    public Path getConfigFilePath() { return configFilePath; }
    public void setConfigFilePath(Path path) { this.configFilePath = path; }

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

        ImportConfigFile config = mapper.readValue(file, ImportConfigFile.class);
        config.setConfigFilePath(file.toPath().toAbsolutePath().getParent());
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);

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
}
