package se.havochvatten.symphonyconfig.setup;

import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.cli.*;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.lang3.ArrayUtils;
import org.geotools.gce.geotiff.GeoTiffFormat;
import se.havochvatten.symphonyconfig.setup.config.*;
import se.havochvatten.symphonyconfig.setup.database.DbInterface;
import se.havochvatten.symphonyconfig.setup.model.Baseline;
import se.havochvatten.symphonyconfig.setup.model.BaselineVersion;
import se.havochvatten.symphonyconfig.setup.model.converter.UpdateModeConverter;
import se.havochvatten.symphonyconfig.setup.process.*;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.geotools.coverage.grid.io.GridFormatFinder.findFormat;
import static se.havochvatten.symphonyconfig.setup.ConfirmImport.confirmToProceed;
import static se.havochvatten.symphonyconfig.setup.SymphonySetupOptionBuilder.newOption;
import static se.havochvatten.symphonyconfig.setup.SymphonySetupVersion.*;
import static se.havochvatten.symphonyconfig.setup.config.SupportedTabularFileFormat.CSV;
import static se.havochvatten.symphonyconfig.setup.model.DbNationalArea.printNationalAreasStatusReport;

public class SymphonySetup {

    private CommandLine setupCmd;
    private DbInterface db;
    private BaselineVersion selectedBaselineVersion;
    private BaselineVersion currentBaselineVersion;

    public static final Options options = new Options();

    public static final Set<String> NationalAreaOptionAliases;
    public static final Set<String> RequiredNewBaselineOptionAliases;

    public static final String DEFAULT_DB_NAME_ENV = "SYMPHONY_DB_NAME";
    public static final String DEFAULT_DB_HOST_ENV = "SYMPHONY_DB_HOST";
    public static final String DEFAULT_DB_USER_ENV = "SYMPHONY_DB_USER";
    public static final String DEFAULT_DB_PASSWORD_ENV = "SYMPHONY_DB_PWD";

    static final String DB_NAME_OPTION = "db";
    static final String DB_HOST_OPTION = "dbH";
    static final String DB_USER_OPTION = "dbU";
    static final String DB_PASSWORD_OPTION = "dbP";

    static final String DB_NAME_ENV_OPTION = "envDb";
    static final String DB_HOST_ENV_OPTION = "envDbH";
    static final String DB_USER_ENV_OPTION = "envDbU";
    static final String DB_PASSWORD_ENV_OPTION = "envDbP";

    static final String USAGE_HEADER = "Command-line utility to manage baseline data for instances of the software package MSP-Symphony";
    static final String USAGE_FOOTER = "For additional usage details, refer to the online documentation https://github.com/havochvatten/symphony-import/blob/main/README.md";

    static final Map<String, String> readableDbSetting =
        Map.of(
            DB_NAME_OPTION, "name",
            DB_USER_OPTION, "user",
            DB_PASSWORD_OPTION, "password"
        );

    static final Set<String> dbOptions = Set.of(
        DB_HOST_OPTION, DB_NAME_OPTION, DB_USER_OPTION, DB_PASSWORD_OPTION
    );

    /**
     * Options that may accompany '-f'. Database connection options are required in
     * file mode; '-bvpE' and '-bvpP' are the documented GeoTIFF path overrides.
     * Everything else must be expressed in the configuration file itself.
     */
    static final Set<String> FILE_MODE_ALLOWED_OPTIONS = Set.of(
        "f",
        "bvpE", "bvpP",
        DB_NAME_OPTION, DB_HOST_OPTION, DB_USER_OPTION, DB_PASSWORD_OPTION,
        "dbS", "dbPt",
        DB_NAME_ENV_OPTION, DB_HOST_ENV_OPTION, DB_USER_ENV_OPTION, DB_PASSWORD_ENV_OPTION
    );

    static {
        Option newBaselineOption = newOption("n", "newBaseline", false,
                    "Install a new baseline version.\n" +
                    "Requires additional options specifying accessible paths for the GeoTIFF data files ('-bvpE', '-bvpP')\n" +
                    "and the '-bvN' option specifying a unique baseline version name.\n" +
                    "Cannot be combined with either of the options 'u' or 'bv'", v1_0),
               updateOption      = newOption("u", "update", true,
                   "Update an existing baseline version. Must be combined with '-bv' option to specify the target baseline version id.\n" +
                   "Takes an optional argument which may be specified as ('u'/'update' or 'r'/'replace'), differentiating \"update mode\".\n" +
                   "When set to 'replace', ALL band metadata for the target baseline version is deleted before the update runs.\n" +
                   "This cascades to every sensitivity score on the baseline, including user-created matrices.\n" +
                   "Sensitivity matrices and calculation areas are not themselves cleared: re-importing them appends duplicates.", v1_0),
               configFileOption  = newOption("f", "file", true,
                    "Pass a json/yaml configuration file with bundled input parameters instead of separate cli options.\n " +
                    "The expected format is documented separately.\n", v1_1),
               statusOption      = newOption("s", "status", false, "Report status of baseline.\n" +
                    "Incompatible in conjunction with most other options.", v1_0),
               verboseOption     = newOption("v", "fullReport", false, "Verbose report.\n" +
                   "Combine with 'status' option for detailed status report.", v1_0),
               helpOption        = newOption("h", "help", false,
                   "Print this usage instruction.", v1_0),

               dbOption          = newOption(DB_NAME_OPTION,   "database", true,
                   "Target database name, required if corresponding environment variable is missing.", v1_0),
               dbuOption         = newOption(DB_USER_OPTION,  "dbUser", true,
                   "Database user (needs write privileges), required if corresponding environment variable is missing.", v1_0),
               dbPwOption        = newOption(DB_PASSWORD_OPTION,  "dbPassword", true,
                   "Clear-text password for the database user, required if corresponding environment variable is missing.", v1_0),
               dbPtOption         = newOption("dbPt", "dbPort", true,
                   "Database port (defaults to 5432)", v1_0),
               dbSOption         = newOption("dbS",  "dbSchema", true, "Database schema (defaults to 'symphony')", v1_0),
               dbHOption         = newOption(DB_HOST_OPTION,  "dbHost", true,
                   "Database host (defaults to 'localhost'), required if corresponding environment variable is missing.", v1_0),

               envDbOption     = newOption(DB_NAME_ENV_OPTION, "envDatabase", true,
                   String.format("Environment variable to specify database name, defaults to %s", DEFAULT_DB_NAME_ENV), v1_1),
               envDbHostOption = newOption(DB_HOST_ENV_OPTION, "envDatabaseHost", true,
                    String.format("Environment variable to specify database host, defaults to %s", DEFAULT_DB_HOST_ENV), v1_1),
               envDbUserOption = newOption(DB_USER_ENV_OPTION, "envDatabaseUser", true,
                    String.format("Environment variable to specify database username, defaults to %s", DEFAULT_DB_USER_ENV), v1_1),
               envDbPasswordOption = newOption(DB_PASSWORD_ENV_OPTION, "envDatabasePassword", true,
                    String.format("Environment variable to specify database password, defaults to %s", DEFAULT_DB_PASSWORD_ENV), v1_1),

               baselineVOption   = newOption("bv",   "baselineVersion", true,
                   "Target baseline version to update. Used in conjunction with the -u option only.", v1_0),
               metadataOption    = newOption("md",   "metadata", true,
                   "Path to metadata file to import (csv or xlsx format is supported).\n" +
                   "Multi-valued option, may be specified repeatedly for multiple languages: " +
                   "when used multivalued it must match with number and order of arguments to the '-mdL' option.", v1_0),
               mdLanguageOption  = newOption("mdL",  "metadataLang", true,
                   "Metadata language as ISO 639-1 code.\n" +
                   "Required when multiple metadata files are given ('-md'), order and number must match exactly.\n" +
                   "For single-file metadata imports this defaults to 'en'", v1_0),

               matrixOption             = newOption("mx", "matrix", true,
                    "Path to sensitivity matrix file to import (presently only csv format is supported).\n" +
                    "Potential multi-valued option for more than one matrix.", v1_0),
               matrixNameOption         = newOption("mxN", "matrixName", true,
                    "Sensitivity matrix name. Required when importing sensitivity matrix/ces.\n" +
                    "Potential multi-valued option to match with the number and order of '-mx' options.", v1_0),
               matrixTitleLangOption    = newOption("mxL", "matrixLang", true,
                    "Matrix titles language for row/column headers, to match with metadata 'title' for the corresponding band.\n" +
                    "Defaults to 'en'", v1_0),

               nationalAreaTypeOption       = newOption("na", "nationalArea", true,
                   "National area type, may be given with multiple arguments.", v1_0),
               nationalAreaPolygonOption    = newOption("naP", "nationalAreaPolygon", true,
                   "National area polygon file. Multi-valued, should match number of arguments to the '-na' option.", v1_0),
               nationalAreaCountryISO       = newOption("naC", "nationalAreaCountryISO", true,
                   "National area country code.", v1_0),

               calcAreaPackageOption        = newOption("caF", "calcAreaFile", true,
                    "Path to GeoPackage file comprising calculation area polygons. See other documentation for expected format and required internal attributes.", v1_0),
               calcAreaNamePropertyOption   = newOption("caP", "calcAreaNameProperty", true,
                   "\"Name property\" to use for calculation area name ('carea_name' column) value in the GeoPackage file specified by '-caF'.\n" +
                       "The default is 'name'.", v1_0),
               calcAreaDefaultOption        = newOption("caD", "calcAreaDefault", true,
                   "Default calculation area. Multi-valued option to correspond with area names present in " +
                           "'-caF' GeoPackage file, specifying default status of the corresponding area.", v1_0),
               calcAreaAllDefaultOption     = newOption("caDA", "calcAreaAllDefault", false,
                   "Specify to set all calculation areas present in the GeoPackage file slated for import by the "+
                           "'-caF' option as default for the target baseline.", v1_0),

               csvDelimOption    = newOption("csvS", "delimiter", true, "Column delimiter character for CSV files. Defaults to ';', the Symphony convention.", v1_0),
               csvNewLineOption  = newOption("csvN", "newline", true, "Row delimiter for CSV files. Set to 'windows' for CRLF input; omitted or any other value means LF.", v1_0),

               newBaselineName  = newOption("bvN", "baselineVersionName", true,
                   "Baseline version name, required for \"new baseline\" invocations ('-n'). Must be unique.", v1_0),
               newBaselineTitle  = newOption("bvT", "baselineVersionTitle", true,
                    "Baseline version title, used in conjunction with ('-n'). Optional.", v1_1),
               newBaselineLocale = newOption("bvL", "baselineVersionLocale", true,
                   "Baseline version locale as ISO 639-1 code, used in conjunction with ('-n'). Defaults to 'en'.", v1_0),
               newBaselineDesc  = newOption("bvD", "baselineVersionDesc", true,
                   "Baseline version description, used in conjunction with ('-n'). Optional.", v1_0),
               newBaselineValidDate = newOption("bvV", "baselineVersionDate", true,
                   "Baseline version \"valid from\"-date as ISO 8601 (YYYY-MM-DD). Defaults to the host system date.", v1_0),
               newBaselineEcoPath = newOption("bvpE", "baselineEcoPath", true,
                   "Baseline version ecosystems GeoTIFF path, required for \"new baseline\" invocations ('-n').", v1_0),
               newBaselinePressurePath = newOption("bvpP", "baselinePressurePath", true,
                   "Baseline version pressures GeoTIFF path, required for \"new baseline\" invocations ('-n').", v1_0);

        Option[] multiValuedOptions = new Option[] {
            metadataOption, mdLanguageOption,
            matrixOption, matrixNameOption, matrixTitleLangOption,
            nationalAreaTypeOption, nationalAreaPolygonOption, nationalAreaCountryISO,
            calcAreaDefaultOption
        };

        updateOption.setOptionalArg(true);

        for (Option option : multiValuedOptions) {
            option.setArgs(Option.UNLIMITED_VALUES);
            option.setValueSeparator(',');
        }

        updateOption.setType(UpdateMode.class);
        updateOption.setConverter(new UpdateModeConverter());

        OptionGroup baseInvocations = new OptionGroup();
        baseInvocations.addOption(newBaselineOption);
        baseInvocations.addOption(updateOption);
        baseInvocations.addOption(statusOption);

        options.addOption(newBaselineOption);
        options.addOption(configFileOption);
        options.addOption(statusOption); options.addOption(verboseOption);
        options.addOption(helpOption);

        options.addOption(dbOption); options.addOption(dbuOption); options.addOption(dbPwOption);
        options.addOption(dbPtOption); options.addOption(dbSOption); options.addOption(dbHOption);

        options.addOption(envDbOption); options.addOption(envDbHostOption);
        options.addOption(envDbUserOption); options.addOption(envDbPasswordOption);

        options.addOption(updateOption); options.addOption(baselineVOption);
        options.addOption(metadataOption); options.addOption(mdLanguageOption);

        options.addOption(nationalAreaTypeOption);  options.addOption(nationalAreaPolygonOption);
        options.addOption(nationalAreaCountryISO);

        options.addOption(matrixOption); options.addOption(matrixNameOption); options.addOption(matrixTitleLangOption);

        options.addOption(calcAreaPackageOption); options.addOption(calcAreaNamePropertyOption);
        options.addOption(calcAreaDefaultOption); options.addOption(calcAreaAllDefaultOption);

        options.addOption(csvDelimOption); options.addOption(csvNewLineOption);

        options.addOption(newBaselineName); options.addOption(newBaselineTitle); options.addOption(newBaselineDesc);
        options.addOption(newBaselineValidDate);
        options.addOption(newBaselineLocale); options.addOption(newBaselineEcoPath); options.addOption(newBaselinePressurePath);

        NationalAreaOptionAliases =
            Set.of(nationalAreaTypeOption.getKey(),
                   nationalAreaPolygonOption.getKey(),
                   nationalAreaCountryISO.getKey());

        RequiredNewBaselineOptionAliases =
            Set.of(newBaselineName.getKey(), newBaselineEcoPath.getKey(), newBaselinePressurePath.getKey());
    }

    public static final Set<String> SYM_LANG = Set.of("en", "fr", "sv");
    public static final Set<String> ISO_LANG = Set.of(Locale.getISOLanguages());

    private CSVSettings getCSVSettings() {
        String sepValue = setupCmd.getOptionValue("csvS");
        String nlValue = setupCmd.getOptionValue("csvN");
        Character separator = sepValue == null ? null : sepValue.charAt(0);
        String newLine =
            nlValue == null || !nlValue.equalsIgnoreCase("windows") ?
                null : "\r\n";

        return new CSVSettings(separator, newLine);
    }

    public UpdateMode updateMode = UpdateMode.UPDATE;
    public boolean clear() { return updateMode == UpdateMode.REPLACE; }

    private boolean failed = false;

    public boolean hasFailed() {
        return failed;
    }

    public SymphonySetup(String[] args) {
        try {
            if (Arrays.stream(args).anyMatch(arg -> arg.equals("--help") || arg.equals("-h"))) {
                HelpFormatter formatter = HelpFormatter.builder().get();
                formatter.printHelp("symphony-setup-tool", USAGE_HEADER, options, USAGE_FOOTER, true);
                System.exit(0);
            }

            setupCmd = new DefaultParser().parse(options, args);

            resolveDbSettings();

            execute();

        } catch (ParseException | IOException e) {
            failed = true;
            System.err.println(e.getMessage());
        }
    }

    private void resolveDbSettings() throws ParseException {
        String dbName = resolveDbOption(DB_NAME_OPTION, DB_NAME_ENV_OPTION, DEFAULT_DB_NAME_ENV);
        String dbHost = resolveDbOption(DB_HOST_OPTION, DB_HOST_ENV_OPTION, DEFAULT_DB_HOST_ENV);
        String dbUser = resolveDbOption(DB_USER_OPTION, DB_USER_ENV_OPTION, DEFAULT_DB_USER_ENV);
        String dbPassword = resolveDbOption(DB_PASSWORD_OPTION, DB_PASSWORD_ENV_OPTION, DEFAULT_DB_PASSWORD_ENV);

        if (!(dbName == null || dbUser == null || dbPassword == null)) {
            db = new DbInterface(
                dbName, dbUser, dbPassword,
                setupCmd.getOptionValue("dbS"), setupCmd.getOptionValue("dbPt"),
                dbHost
            );
            return;
        }
        String errorMessage = "";

        if (dbName == null)     errorMessage += missingRequiredDbSettingMessage(DB_NAME_OPTION);
        if (dbUser == null)     errorMessage += missingRequiredDbSettingMessage(DB_USER_OPTION);
        if (dbPassword == null) errorMessage += missingRequiredDbSettingMessage(DB_PASSWORD_OPTION);

        throw new ParseException(errorMessage);
    }

    private void checkExistingBaselineName(String baselineVersionName) throws ParseException, SQLException {
        Integer bvId = db.baselineVersionIdByName(baselineVersionName);

        if (bvId != null) {
            throw new ParseException(
                String.format("Error: The provided baseline version name '%s' already exists.%nIts id in the database is: %d",
                        baselineVersionName, bvId));
        }
    }

    private void checkExistingBaselineValidFrom(LocalDate validFrom) throws ParseException, SQLException {
        if (db.baselineVersionExistsForDate(java.sql.Date.valueOf(validFrom))) {
            throw new ParseException(String.format(
                "A baseline version with validFrom '%s' already exists.%n"
                    + "MSP-Symphony resolves the current baseline by validFrom and fails with "
                    + "BASELINE_VERSION_MULT_MATCHES when two share a date. "
                    + "Set an explicit, unique 'baseline.validFrom' in the configuration file.",
                validFrom));
        }
    }

    private void checkNewBaselineInvocation() throws ParseException, SQLException {
        if (RequiredNewBaselineOptionAliases.stream().allMatch(setupCmd::hasOption)) {
            Integer optBvId = Util.tryParseInt(setupCmd.getOptionValue("bv"));
            if (optBvId != null) {
                throw new ParseException(String.format(
                    "Error: ambiguos invocation.%n-n and -bv options cannot be issued at the same time.")
                );
            }
            String baselineVersionName = setupCmd.getOptionValue("bvN");
            checkExistingBaselineName(baselineVersionName);

        } else {
            throw new ParseException("");
        }
    }

    private boolean checkNationalAreaInvocation() throws ParseException {
        if (NationalAreaOptionAliases.stream().anyMatch(setupCmd::hasOption)) {
            boolean allRequired = NationalAreaOptionAliases.stream().allMatch(setupCmd::hasOption),
                correctOptions = Arrays.stream(optionsExceptRequired()).allMatch(NationalAreaOptionAliases::contains);

            if (allRequired && correctOptions) {
                boolean includesBoundaryType =
                    Arrays.asList(setupCmd.getOptionValues("nationalArea")).contains("BOUNDARY");

                if (!includesBoundaryType) {
                    throw new ParseException("National area import must include the BOUNDARY type");
                }

                return true;
            }
            if (!correctOptions) {
                    throw new ParseException("Invalid invocation:\n" +
                        "both baseline and national area import options were provided.");
            }
            // implying allRequired is false
            throw new ParseException("Invalid invocation:\n" +
                "some required national area import option was missing.\n " +
                "(-na, -naP, -naC are all required for the national area import procedure).");
        }
        return false;
    }

    private String[] optionsExceptRequired() {
        return Arrays.stream(setupCmd.getOptions())
            .map(Option::getKey)
            .filter(key -> !dbOptions.contains(key))
            .toArray(String[]::new);
    }

    private void setBaselineVersion() throws ParseException, SQLException {
        currentBaselineVersion = db.getBaselineVersion(null);

        if (currentBaselineVersion == null) {
            throw new ParseException("No baseline version is present in the target database.\n" +
                    "Use the -n option to install compliant baseline data");
        }

        selectedBaselineVersion = getBaselineVersion();

        if (selectedBaselineVersion == null && setupCmd.hasOption("bv")) {
            throw new ParseException("Baseline version with id " + setupCmd.getOptionValue("bv") +
                    " was not found in the target database. Aborting.");
        }
    }

    private void importBaselineData() throws ParseException {
        // 'n' = Import new baseline
        // 'u' = Update existing baseline
        if (setupCmd.hasOption("u") || setupCmd.hasOption("n")) {

            try {
                if (setupCmd.hasOption("u") && setupCmd.hasOption("n")) {
                    throw new ParseException("Invalid invocation:\n" +
                        "Either 'u' or 'n' options can be used, " +
                        "but not both.");
                }

                if (setupCmd.hasOption("u")) {
                    setBaselineVersion();
                    if (selectedBaselineVersion == null) return;

                    // Currently unreachable: the value of '-u' is never parsed
                    // (UpdateModeConverter is registered on the option, but
                    // getParsedOptionValue is never called), so updateMode stays UPDATE
                    // and clear() is always false on the switch path. Deliberate
                    // insurance, so that fixing that wiring inherits the guard rather
                    // than reintroducing the destructive behaviour through the CLI.
                    guardReplaceMode();
                }

                if (setupCmd.hasOption("n")) {
                    updateMode = UpdateMode.UPDATE;

                    Scanner prompt = new Scanner(System.in);
                    String pendingNewBaselineName = setupCmd.getOptionValue("bvN");

                    checkNewBaselineInvocation();

                    if (!confirmToProceed(
                        String.format("Pending baseline version installation: %s", pendingNewBaselineName),
                        "Baseline version installation aborted interactively.")) {
                        return;
                    }

                    selectedBaselineVersion = db.getBaselineVersion(importNewBaselineVersion());
                }

                if (setupCmd.hasOption("md")) {
                    importMetadata();
                }

                if (setupCmd.hasOption("mx")) {
                    importSensitivityMatrix();
                }

                if (setupCmd.hasOption("caF")) {
                    importCalculationAreaPolygons();
                }

            } catch (Exception e) {
                throw new ParseException(e.getMessage());
            }
        }
    }

    private void importNationalAreas() throws ParseException {
        String[] areaTypes = setupCmd.getOptionValues("nationalArea"),
            areaPolygonPaths = setupCmd.getOptionValues("nationalAreaPolygon"),
            areaCountryISO = setupCmd.getOptionValues("nationalAreaCountryISO");

        // method will be called after checking that the options are set
        // area types and polygons must be same length, country ISO must either be single argument or same as other two
        if (areaTypes.length == areaPolygonPaths.length &&
           (areaTypes.length == areaCountryISO.length || areaCountryISO.length == 1)) {
            NationalAreaRowInsert[] areaInserts = new NationalAreaRowInsert[areaTypes.length];

            for (int ti = 0; ti < areaTypes.length; ++ti) {
                String iso = areaCountryISO.length == areaTypes.length ? areaCountryISO[ti] : areaCountryISO[0];
                areaInserts[ti] = new NationalAreaRowInsert(areaTypes[ti], iso, areaPolygonPaths[ti]);
            }

            Scanner prompt = new Scanner(System.in);
            String areaIdentifiers = String.join(", ", areaTypes);

            if (!confirmToProceed(
                    String.format("Pending national areas import: %s", areaIdentifiers),
                    "National areas import aborted interactively.")) {
                return;
            }

            try {
                db.updateNationalAreas(areaInserts);
            } catch (SQLException e) {
                throw new ParseException(e.getMessage());
            }
        } else {
            if (areaTypes.length != areaPolygonPaths.length) {
                throw new ParseException("National area polygon path arguments must match number of specified area types.");
            } else {
                throw new ParseException("Area country ISO arguments must match number of specified area types, or be single.");
            }
        }
    }


    private void executeFromConfigFile() throws ParseException {
        String configFilePath = setupCmd.getOptionValue("f");

        try {
            ImportConfigFile config = ImportConfigFile.parse(configFilePath);

            // Validate the configuration in full before any database write occurs
            config.validate();

            switch (config.getOperation()) {
                case NEW_BASELINE -> executeNewBaselineFromConfig(config);
                case UPDATE -> executeUpdateFromConfig(config);
                case NATIONAL_AREAS -> executeNationalAreasFromConfig(config);
            }

        } catch (IOException e) {
            throw new ParseException("Error reading configuration file: " + e.getMessage());
        }
    }

    private void validateLocale(String locale) throws ParseException {
        if (!ISO_LANG.contains(locale)) {
            throw new ParseException(
                String.format("The provided baseline locale ('%s'), is not a valid ISO 639-1 language code.", locale));
        }
    }

    private void commonConfigImportSequence(ImportConfigFile config) throws Exception {
        // Import metadata if specified
        if (config.getMetadata() != null && !config.getMetadata().isEmpty()) {
            importMetadataFromConfig(config);
        }

        // Import matrices if specified
        if (config.getMatrices() != null && !config.getMatrices().isEmpty()) {
            importMatricesFromConfig(config);
        }

        // Import calculation areas if specified
        if (config.getCalculationAreas() != null) {
            importCalculationAreasFromConfig(config);
        }
    }

    private void executeNewBaselineFromConfig(ImportConfigFile config) throws ParseException {
        // Retained as a guard only: ImportConfigFile.validate() fires first on the '-f' path.
        if (config.getBaseline() == null) {
            throw new ParseException("Configuration must include 'baseline' section for newBaseline operation");
        }

        ImportConfigFile.BaselineConfig bl = config.getBaseline();

        // Required fields for new baseline
        // Retained as a guard only: ImportConfigFile.validate() fires first on the '-f' path.
        if (bl.getName() == null || bl.getName().isEmpty()) {
            throw new ParseException("baseline.name is required for newBaseline operation");
        }

        // Apply CLI overrides for GeoTIFF paths if provided
        String ecoPath = setupCmd.hasOption("bvpE") ?
            setupCmd.getOptionValue("bvpE") : config.resolvePath(bl.getEcoPath());
        String pressurePath = setupCmd.hasOption("bvpP") ?
            setupCmd.getOptionValue("bvpP") : config.resolvePath(bl.getPressurePath());

        if (ecoPath == null || pressurePath == null) {
            throw new ParseException("Paths for the (Ecosystem and Pressure) GeoTIFF raster files must be supplied for newBaseline operation");
        }

        try {
            // Check for duplicate baseline name
            checkExistingBaselineName(bl.getName());

            // Validate GeoTIFF files
            validateGeoTiffPath(ecoPath, "Ecosystem");
            validateGeoTiffPath(pressurePath, "Pressure");

            // Validate locale
            String locale = bl.getLocale() != null ? bl.getLocale() : "en";
            validateLocale(locale);

            // Parse valid from date
            LocalDate validFrom = bl.getValidFrom() != null ?
                LocalDate.parse(bl.getValidFrom()) : LocalDate.now();

            checkExistingBaselineValidFrom(validFrom);

            // Only ask the operator to confirm an import that is known to be valid
            if (!confirmToProceed(
                    String.format("Pending baseline version installation: %s", bl.getName()),
                    "Baseline version installation aborted interactively.")) {
                return;
            }

            // Create and insert baseline version
            BaselineVersion baselineVersionToInstall = new BaselineVersion(
                bl.getName(),
                bl.getTitle(),
                bl.getDescription() != null ? bl.getDescription() : "",
                validFrom,
                ecoPath,
                pressurePath,
                locale
            );

            int bvId = db.insertBaselineVersion(baselineVersionToInstall);
            selectedBaselineVersion = db.getBaselineVersion(bvId);
            updateMode = UpdateMode.UPDATE;

            commonConfigImportSequence(config);

        } catch (SQLException e) {
            throw new ParseException("Database error: " + e.getMessage());
        } catch (Exception e) {
            throw new ParseException(e.getMessage());
        }
    }

    private void executeUpdateFromConfig(ImportConfigFile config) throws ParseException {
        // Retained as a guard only: ImportConfigFile.validate() fires first on the '-f' path.
        if (config.getBaseline() == null) {
            throw new ParseException("Configuration must include 'baseline' section for update operation");
        }

        ImportConfigFile.BaselineConfig bl = config.getBaseline();

        // Retained as a guard only: ImportConfigFile.validate() fires first on the '-f' path.
        if (bl.getId() == null) {
            throw new ParseException("baseline.id is required for update operation");
        }

        try {
            selectedBaselineVersion = db.getBaselineVersion(bl.getId());
            if (selectedBaselineVersion == null) {
                throw new ParseException("Baseline version with id " + bl.getId() +
                    " was not found in the target database. Aborting.");
            }

            // Set update mode (default to UPDATE if not specified)
            updateMode = bl.getUpdateMode() != null ? 
                bl.getUpdateMode() : UpdateMode.UPDATE;

            guardReplaceMode();

            commonConfigImportSequence(config);

        } catch (SQLException e) {
            throw new ParseException("Database error: " + e.getMessage());
        } catch (Exception e) {
            throw new ParseException(e.getMessage());
        }
    }

    private void executeNationalAreasFromConfig(ImportConfigFile config) throws ParseException {
        // The 'nationalAreas' section, its BOUNDARY entry and every referenced file are
        // verified by ImportConfigFile.validate() before this method is reached.

        // Convert to NationalAreaRowInsert array
        NationalAreaRowInsert[] areaInserts = config.getNationalAreas().stream()
            .map(na -> new NationalAreaRowInsert(
                na.getType(),
                na.getCountryISO(),
                config.resolvePath(na.getFile())
            ))
            .toArray(NationalAreaRowInsert[]::new);

        Scanner prompt = new Scanner(System.in);
        String areaIdentifiers = config.getNationalAreas().stream()
            .map(ImportConfigFile.NationalAreaConfig::getType)
            .collect(Collectors.joining(", "));

        if (!confirmToProceed(
                String.format("Pending national areas import: %s", areaIdentifiers),
                "National areas import aborted interactively.")) {
            return;
        }

        try {
            db.updateNationalAreas(areaInserts);
        } catch (SQLException e) {
            throw new ParseException("Database error: " + e.getMessage());
        }
    }

    /**
     * Pre-flight checks for updateMode 'replace', which clears all band metadata for the
     * selected baseline version before re-importing.
     * <p>
     * Two separate risks, handled differently:
     * <ul>
     *   <li>reliabilitypartition rows reference meta_bands with no ON DELETE action, so the
     *       clear is guaranteed to fail part-way through. There is no way to complete it, so
     *       the run is refused outright.</li>
     *   <li>sensitivity scores cascade away with their bands, emptying every user-created
     *       sensitivity matrix on the baseline. The operator may well intend this, so it is a
     *       warning, but it names the owners so nobody discovers it afterwards.</li>
     * </ul>
     */
    private void guardReplaceMode() throws ParseException, SQLException {
        if (!clear() || selectedBaselineVersion == null) {
            return;
        }

        int reliabilityRows = db.countReliabilityPartitionRows(selectedBaselineVersion.getId());
        if (reliabilityRows > 0) {
            throw new ParseException(String.format(
                "Cannot run updateMode 'replace' on this baseline version: %d reliability "
                    + "partition polygon(s) reference its band metadata.%n"
                    + "Clearing band data would fail part-way through and leave the baseline "
                    + "in a broken state. Remove the reliability partitions first, or use "
                    + "updateMode 'update'.",
                reliabilityRows));
        }

        List<String> owners = db.ownedMatrixOwners(selectedBaselineVersion.getId());
        if (!owners.isEmpty()) {
            System.out.printf(
                "WARNING: updateMode 'replace' deletes all band metadata for this baseline "
                    + "version.%nThis cascades to every sensitivity score on it, including "
                    + "the user-created matrices owned by: %s%n"
                    + "Those matrices will remain listed but will be empty. "
                    + "This cannot be undone by this tool.%n",
                String.join(", ", owners));
        }
    }

    private void importMetadataFromConfig(ImportConfigFile config) throws Exception {
        String defaultLang = selectedBaselineVersion.getLocale();

        for (int i = 0; i < config.getMetadata().size(); ++i) {
            ImportConfigFile.MetadataConfig mdConfig = config.getMetadata().get(i);
            String resolvedPath = config.resolvePath(mdConfig.getFile());
            String language = mdConfig.getLanguage() != null ? mdConfig.getLanguage() : defaultLang;

            MetadataImportSettings mdSettings = new MetadataImportSettings(
                selectedBaselineVersion,
                resolvedPath,
                language,
                defaultLang,
                clear() && i == 0,   // clear once for the whole update, not once per file
                i
            );

            if (!mdSettings.validate()) {
                throw new ParseException(mdSettings.errorMessage());
            }

            String parsingMessage = mdSettings.parsingMessage();
            if (parsingMessage != null) {
                System.out.println(parsingMessage);
            }

            MetadataBase md;
            switch (mdSettings.format) {
                case CSV -> md = new MetadataCsv(mdSettings, getCSVSettings(config));
                case XLSX -> md = new MetadataXlsx(mdSettings);
                default -> throw new ParseException("Unknown metadata file format");
            }

            db.updateMetadata(md);
            defaultLang = language; // Carry forward for next iteration
        }
    }

    private void importMatricesFromConfig(ImportConfigFile config) throws Exception {
        Baseline selectedBaseline = db.getBaseline(selectedBaselineVersion.getId());

        if (selectedBaseline.isMetaIncomplete()) {
            throw new ParseException("Matrix import is not possible for baseline version " +
                "with incomplete meta band coverage");
        }

        String defaultLang = selectedBaselineVersion.getLocale();

        for (int i = 0; i < config.getMatrices().size(); ++i) {
            ImportConfigFile.MatrixConfig mxConfig = config.getMatrices().get(i);

            // Retained as a guard only: ImportConfigFile.validate() fires first on the '-f' path.
            if (mxConfig.getName() == null || mxConfig.getName().isEmpty()) {
                throw new ParseException("Matrix name is required for each matrix in the configuration");
            }

            String resolvedPath = config.resolvePath(mxConfig.getFile());
            String language = mxConfig.getLanguage() != null ? mxConfig.getLanguage() : defaultLang;

            MatrixImportSettings mxSettings = new MatrixImportSettings(
                selectedBaselineVersion,
                resolvedPath,
                language,
                defaultLang,
                clear() && i == 0,   // clear once for the whole update, not once per file
                i
            );

            mxSettings.setMatrixName(mxConfig.getName());

            if (!mxSettings.validate()) {
                throw new ParseException(mxSettings.errorMessage());
            }

            String parsingMessage = mxSettings.parsingMessage();
            if (parsingMessage != null) {
                System.out.println(parsingMessage);
            }

            MatrixBase mx;
            if (Objects.requireNonNull(mxSettings.format) == CSV) {
                mx = new MatrixCsv(mxSettings, selectedBaseline, getCSVSettings(config));
            } else {
                throw new ParseException("Unknown sensitivity matrix file format");
            }

            db.updateMatrix(mx);
            defaultLang = language; // Carry forward for next iteration
        }
    }

    private void importCalculationAreasFromConfig(ImportConfigFile config) throws ParseException, SQLException {
        ImportConfigFile.CalculationAreasConfig caConfig = config.getCalculationAreas();

        // 'calculationAreas.file' is verified by ImportConfigFile.validate() before this point.
        String resolvedPath = config.resolvePath(caConfig.getFile());
        String nameProperty = caConfig.getNameProperty() != null ? caConfig.getNameProperty() : "name";
        boolean allDefault = caConfig.getAllDefault() != null && caConfig.getAllDefault();
        String[] defaultAreas = caConfig.getDefaultAreas() != null ?
            caConfig.getDefaultAreas().toArray(new String[0]) : null;

        CalcAreaProcedure calcAreaProcedure = new CalcAreaProcedure(
            new CalcAreaImportSettings(
                selectedBaselineVersion,
                resolvedPath,
                nameProperty,
                clear(),
                allDefault,
                defaultAreas,
                db.getAvailableAreaTypes(),
                db.getMatrixMap(selectedBaselineVersion.getId())
            )
        );

        if (calcAreaProcedure.confirmImport()) {
            db.importCalculationAreas(calcAreaProcedure.areaTuples, selectedBaselineVersion.getId());
        }
    }

    private void validateGeoTiffPath(String path, String type) throws ParseException {
        Path filePath = Path.of(path).normalize();

        if (!Files.exists(filePath)) {
            throw new ParseException(String.format("%s raster file not found: %s", type, path));
        }

        if (!Files.isReadable(filePath)) {
            throw new ParseException(String.format("%s raster file not readable: %s", type, path));
        }

        if (!(findFormat(new File(path)) instanceof GeoTiffFormat)) {
            throw new ParseException(String.format("File is not a valid GeoTiff raster: %s", path));
        }
    }

    private CSVSettings getCSVSettings(ImportConfigFile config) {
        if (config.getCsvSettings() != null) {
            ImportConfigFile.CsvSettingsConfig csvConfig = config.getCsvSettings();
            Character delimiter = csvConfig.getDelimiter() != null ?
                csvConfig.getDelimiter().charAt(0) : null;
            String newLine = csvConfig.getNewline() != null &&
                csvConfig.getNewline().equalsIgnoreCase("windows") ? "\r\n" : null;
            return new CSVSettings(delimiter, newLine);
        }
        return new CSVSettings(null, null);
    }

    private void execute() throws ParseException {
        if (setupCmd.hasOption("v") && !setupCmd.hasOption("s")) {
            throw new ParseException("'Verbose report' option is only valid in combination with -s/--status.");
        }

        // 'f' = Configuration file option passed (path)
        if (setupCmd.hasOption("f")) {
            String disallowed = Arrays.stream(setupCmd.getOptions())
                .map(Option::getOpt)
                .filter(opt -> !FILE_MODE_ALLOWED_OPTIONS.contains(opt))
                .sorted()
                .collect(Collectors.joining(", "));

            if (!disallowed.isEmpty()) {
                throw new ParseException(String.format(
                    "ERROR: When passing the 'file' input argument, these switches are disallowed: %s.%n"
                        + "Express these settings in the configuration file instead. "
                        + "Only the GeoTIFF path overrides '-bvpE' and '-bvpP' and the database "
                        + "connection options may accompany '-f'.",
                    disallowed));
            }

            executeFromConfigFile();
        } else if (setupCmd.hasOption("s")) {
            // 's' = Status option - print state of baseline
            try {
                setBaselineVersion();
                if (selectedBaselineVersion == null) return;

                Baseline baselineToReport = db.getBaselineForReport(selectedBaselineVersion.getId());
                baselineToReport.printStatusReport(setupCmd.hasOption("v"));

                if (setupCmd.hasOption("v")) {
                    // Full report, include 'national areas' status
                    printNationalAreasStatusReport(db.getAllNationalAreas());
                }

            } catch (Exception e) {
                throw new ParseException(e.getMessage());
            }

        } else if (checkNationalAreaInvocation()) {
            importNationalAreas();
        } else {
            importBaselineData();
        }
    }

    private BaselineVersion getBaselineVersion() throws SQLException {
        Integer bvId = Util.tryParseInt(setupCmd.getOptionValue("bv"));

        if (bvId == null) {
            int[] blvList = db.getAvailableBaselineVersionIds();
            String blvList_s = Util.join(blvList, ", ");
            System.out.print(
                String.format("Tool invoked without specifying baseline version id.\n\n" +
                    "Available options:\n" +
                    "- [ Numeric input ]\tEnter an existing baseline version id to target.\n" +
                    "                   \tAvailable baseline version ids = (%s)\n" +
                    "- [   a / n / q   ]\tAbort\n" +
                    "- [       d       ]\tDefault to the latest baseline version (id: %d)\n\n" +
                    "> ", blvList_s, currentBaselineVersion.getId())
            );

            while (bvId == null) {
                Scanner prompt = new Scanner(System.in);
                String input = prompt.nextLine().trim();

                if ((bvId = Util.tryParseInt(input)) != null) {
                    if (!ArrayUtils.contains(blvList, bvId)) {
                        System.out.println("Invalid baseline version id: " + bvId);
                        System.out.println("Available ids : " + blvList_s);
                        System.out.print("Try again.\n\n> ");
                        bvId = null;
                        continue;
                    } else {
                        break;
                    }
                }

                if (input.equals("a") || input.equals("n") || input.equals("q")) {
                    System.out.println("Task aborted interactively.");
                    return null;
                }

                if (input.equals("d")) {
                    // Note: bvId equaling null
                    break;
                }

                System.out.print("Unrecognized input. Try again.\n\n> ");
            }
        }

        return db.getBaselineVersion(bvId);
    }

    private <T extends TextualSettingsBase> List<T> processSettings(String opt, String langOpt, Class<T> settingsType)
        throws Exception {
        List<T> settings = new ArrayList<>();
        String[] files = setupCmd.getOptionValues(opt);
        String[] languageParams = setupCmd.getOptionValues(langOpt);
        String currentDefaultLang = selectedBaselineVersion.getLocale();

        for (int i = 0; i < files.length; ++i) {
            boolean hasLang = languageParams != null && languageParams.length > i;

            T settingObj = TextualSettingsBase.create(settingsType, selectedBaselineVersion, files[i],
                                hasLang ? languageParams[i] : null, currentDefaultLang, clear(), i);

            if (!settingObj.validate()) {
                throw new ParseException(settingObj.errorMessage());
            }

            settings.add(settingObj);
            String parsingMessage = settingObj.parsingMessage();

            if (parsingMessage != null) {
                System.out.println(parsingMessage);
            }

            currentDefaultLang = hasLang ? languageParams[i] : currentDefaultLang;
        }

        return settings;
    }

    private void importMetadata() throws Exception {
        List<MetadataImportSettings> metadataToImport =
            processSettings("md", "mdL", MetadataImportSettings.class);

        for (MetadataImportSettings mdSettings : metadataToImport) {
            MetadataBase md;

            switch (mdSettings.format) {
                case CSV -> md = new MetadataCsv(mdSettings, getCSVSettings());
                case ODS -> throw new ParseException("Metadata as ODS not implemented");
                case XLSX -> md = new MetadataXlsx(mdSettings);
                default -> throw new ParseException("Unknown metadata file format");
            }

            db.updateMetadata(md);
        }
    }

    private void importSensitivityMatrix() throws Exception {
        Baseline selectedBaseline = db.getBaseline(selectedBaselineVersion.getId());

        if (selectedBaseline.isMetaIncomplete()) {
            throw new ParseException("Matrix import is not possible for baseline version " +
                                     "with incomplete meta band coverage");
        }

        String[] matrixNames, matrixFiles;

        matrixFiles = setupCmd.getOptionValues("mx");
        matrixNames = setupCmd.getOptionValues("mxN");

        if (!(setupCmd.hasOption("mxN"))) {
            throw new ParseException("Matrix import: Matrix name ('-mxN') must be specified");
        }

        if (matrixFiles.length > matrixNames.length) {
            throw new ParseException(
                String.format("Too few matrix names: %d expected, %d provided",
                              matrixFiles.length, matrixNames.length)
            );
        }

        List<MatrixImportSettings> matricesToImport =
            processSettings("mx", "mxL", MatrixImportSettings.class);

        for (int i = 0; i < matricesToImport.size(); ++i) {
            MatrixBase mx;

            MatrixImportSettings mxSetting = matricesToImport.get(i);
            mxSetting.setMatrixName(matrixNames[i]);

            switch (mxSetting.format) {
                case CSV -> mx = new MatrixCsv(mxSetting, selectedBaseline, getCSVSettings());
                case ODS -> throw new ParseException("Sensitivity matrix as ODS not implemented");
                case XLSX -> throw new ParseException("");
                default -> throw new ParseException("Unknown sensitivity matrix file format");
            }

            db.updateMatrix(mx);
        }
    }

    private void importCalculationAreaPolygons() throws ParseException, SQLException {
        String  caNameProperty = setupCmd.hasOption("caP") ?
                                 setupCmd.getOptionValue("caP") : "name";

        CalcAreaProcedure calcAreaProcedure =
            new CalcAreaProcedure(
                new CalcAreaImportSettings(
                    selectedBaselineVersion,
                    setupCmd.getOptionValue("caF"),
                    caNameProperty,
                    clear(),
                    setupCmd.hasOption("caDA"),
                    setupCmd.getOptionValues("caD"),
                    db.getAvailableAreaTypes(),
                    db.getMatrixMap(selectedBaselineVersion.getId()))
            );

        if (calcAreaProcedure.confirmImport()) {
            db.importCalculationAreas(calcAreaProcedure.areaTuples, selectedBaselineVersion.getId());
        }
    }

    private int importNewBaselineVersion() throws ParseException, SQLException {
        String bvDescription = setupCmd.hasOption("bvD") ? setupCmd.getOptionValue("bvD") : "",
            bvLocale = setupCmd.hasOption("bvL") ? setupCmd.getOptionValue("bvL") : "en";
        LocalDate bvValidDate;

        if (setupCmd.hasOption("bvV")) {
            String bvDateOption = setupCmd.getOptionValue("bvV");
            try {
                bvValidDate = LocalDate.parse(bvDateOption);
            } catch (DateTimeParseException e) {
                throw new ParseException(
                    String.format("Date provided in an invalid format: %s\nTry again using ISO 8601 (YYYY-MM-DD) for the input.", bvDateOption));
            }
        } else {
            bvValidDate = LocalDate.now();
        }

        checkExistingBaselineValidFrom(bvValidDate);

        for (Option rasterOption: new Option[]{ options.getOption("bvpE"), options.getOption("bvpP") }) {
            String rasterFilePathValue = setupCmd.getOptionValue(rasterOption);
            validateGeoTiffPath(rasterFilePathValue, rasterOption.getKey().equals("bvpE") ? "Ecosystem" : "Pressure");
        }

        if (!ISO_LANG.contains(bvLocale)) {
            throw new ParseException(
                String.format("The provided baseline locale ('%s'), is not a valid ISO 639-1 language code.", bvLocale));
        }

        BaselineVersion baselineVersionToInstall = new BaselineVersion(
            setupCmd.getOptionValue("bvN"),
            setupCmd.getOptionValue("bvT"),
            bvDescription,
            bvValidDate,
            setupCmd.getOptionValue("bvpE"),
            setupCmd.getOptionValue("bvpP"),
            bvLocale
        );

        return db.insertBaselineVersion(baselineVersionToInstall);
    }

    String resolveDbOption(String optionKey, String envOption, String env) {
        if (setupCmd.hasOption(optionKey)) return setupCmd.getOptionValue(optionKey);
        return System.getenv(setupCmd.hasOption(envOption) ? setupCmd.getOptionValue(envOption) : env);
    }

    private String missingRequiredDbSettingMessage(String optionKey) {
        return String.format(
            "Required setting for database %s was not provided.%n", readableDbSetting.get(optionKey)
        );
    }

    public static class Util {
        public static Integer tryParseInt(String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public static final String STATUS_SECTION_SEPARATOR  = "----------------------------------------";
        public static final String STATUS_SECTION_DSEPARATOR = "========================================";
        public static final String CHECKMARK = supportedCheckmark();

        public static Set<String> jsArrayToSet(String json) {
            Set set = new HashSet<>();

            if (json == null || json.isEmpty()) { return set; }
            String trim = json.trim();

            if (!trim.startsWith("[") || !trim.endsWith("]")) { return set; }

            Matcher matcher = JSON_STRING_ARRAY_RX.matcher(trim.substring(1, trim.length() - 1));
            while (matcher.find()) {
                set.add(matcher.group(1));
            }

            return set;
        }

        private static final Pattern JSON_STRING_ARRAY_RX = Pattern.compile("\"([a-zA-Z0-9_]+)\"");
        private static String supportedCheckmark() {
            try {
                String encoding = Charset.defaultCharset().displayName();
                if (encoding != null && encoding.toUpperCase().contains("UTF")) {
                    return "\u2713";
                }
            } catch (Exception ignored) {
            }
            return "[OK]";
        }

        public static String getValidIndicator(boolean condition, String invalid) {
            return condition ? CHECKMARK : invalid;
        }

        @Nullable
        public static Boolean parseNullableBoolean(String str) {
            if (str == null) return null;

            String _str = str.trim().toLowerCase();
            if (_str.equalsIgnoreCase("true") || _str.equalsIgnoreCase("1")) {
                return true;
            }
            if (_str.equalsIgnoreCase("false") || _str.equalsIgnoreCase("0")) {
                return false;
            }
            return null;
        }

        public static String join(int[] array, String separator) {
            return Arrays.stream(array)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(separator));
        }

        public static String ordinal(int number) {
            return switch (number % 10) {
                case 1 -> String.format("%dst", number);
                case 2 -> String.format("%dnd", number);
                case 3 -> String.format("%drd", number);
                default -> String.format("%dth", number);
            };
        }
    }

    public enum UpdateMode {
        UPDATE("update"),
        REPLACE("replace");

        @JsonValue
        private final String value;

        UpdateMode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
