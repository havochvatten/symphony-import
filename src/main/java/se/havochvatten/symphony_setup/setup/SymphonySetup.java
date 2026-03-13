package se.havochvatten.symphony_setup.setup;

import org.apache.commons.cli.*;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.lang3.ArrayUtils;
import org.geotools.gce.geotiff.GeoTiffFormat;
import se.havochvatten.symphony_setup.setup.config.*;
import se.havochvatten.symphony_setup.setup.database.DbInterface;
import se.havochvatten.symphony_setup.setup.model.Baseline;
import se.havochvatten.symphony_setup.setup.model.BaselineVersion;
import se.havochvatten.symphony_setup.setup.model.converter.MatrixCalcAreaConverter;
import se.havochvatten.symphony_setup.setup.model.converter.BooleanYesNoConverter;
import se.havochvatten.symphony_setup.setup.model.converter.UpdateModeConverter;
import se.havochvatten.symphony_setup.setup.model.option.CalcAreaOption;
import se.havochvatten.symphony_setup.setup.process.*;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
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
import static se.havochvatten.symphony_setup.setup.model.DbNationalArea.printNationalAreasStatusReport;

public class SymphonySetup {

    private CommandLine setupCmd;
    private DbInterface db;
    private BaselineVersion selectedBaselineVersion;
    private BaselineVersion currentBaselineVersion;

    public static final Options options = new Options();

    public static final Set<String> NationalAreaOptionAliases;
    public static final Set<String> RequiredNewBaselineOptionAliases;

    public static final String USAGE_HEADER = "Command-line utility to manage baseline data for instances of the software package MSP-Symphony";
    public static final String USAGE_FOOTER = "For additional usage details, refer to the online documentation https://github.com/havochvatten/symphony-import/blob/main/README.md";

    static {
        Option newBaselineOption = new Option("n", "newBaseline", false,
                    "Install a new baseline version.\n" +
                    "Requires additional options specifying accessible paths for the GeoTIFF data files ('-bvpE', '-bvpP')\n" +
                    "and the '-bvN' option specifying a unique baseline version name.\n" +
                    "Cannot be combined with either of the options 'u' or 'bv'"),
               updateOption      = new Option("u", "update", true,
                   "Update an existing baseline version. Must be combined with '-bv' option to specify the target baseline version id.\n" +
                   "Takes an optional argument which may be specified as ('u'/'update' or 'r'/'replace'), differentiating \"update mode\".\n" +
                   "When set to 'replace', all existing coupled data is cleared before the update procedure is run."),
               configFileOption  = new Option("f", "file", true,
                   "NOT IMPLEMENTED!\n" +
                    "This option will allow passing a json/yaml configuration file instead of separate cli options. " +
                    "Provided as a placeholder, not currently implemented. Planned for v1.1 of the tool."),
               statusOption      = new Option("s", "status", false, "NOT IMPLEMENTED"),
               verboseOption     = new Option("v", "fullReport", false, "Verbose report.\n" +
                   "Combine with 'status' option for detailed status report."),
               helpOption        = new Option("h", "help", false,
                   "Print this usage instruction."),

               dbOption          = new Option("db",   "database", true,
                   "Required option, specifying the target database name"),
               dbuOption         = new Option("dbU",  "dbUser", true,
                   "Required option, specifying the database user (needs write privileges)"),
               dbPwOption        = new Option("dbP",  "dbPassword", true,
                   "Required option, specifying the (clear-text) database password for the db user."),
               dbPtOption         = new Option("dbPt", "dbPort", true,
                   "Database port (defaults to 5432)"),
               dbSOption         = new Option("dbS",  "dbSchema", true, "Database schema (defaults to 'symphony')"),
               dbHOption         = new Option("dbH",  "dbHost", true,
                   "Database host (defaults to 'localhost')"),

               baselineVOption   = new Option("bv",   "baselineVersion", true,
                   "Target baseline version to update. Used in conjunction with the -u option only."),
               metadataOption    = new Option("md",   "metadata", true,
                   "Path to metadata file to import (csv or xlsx format is supported).\n" +
                   "Multi-valued option, may be specified repeatedly for multiple languages:" +
                   "when used multivalued it must match with number and order of arguments to the '-mdL' option."),
               mdLanguageOption  = new Option("mdL",  "metadataLang", true,
                   "Metadata language as ISO 639-1 code.\n" +
                   "Required when multiple metadata files are given ('-md'), order and number must match exactly.\n" +
                   "For single-file metadata imports this defaults to 'en'"),

               matrixOption             = new Option("mx", "matrix", true,
                    "Path to sensitivity matrix file to import (presently only csv format is supported).\n" +
                    "Potential multi-valued option for more than one matrix."),
               matrixNameOption         = new Option("mxN", "matrixName", true,
                    "Sensitivity matrix name. Required when importing sensitivity matrix/ces.\n" +
                    "Potential multi-valued option to match with the number and order of '-mx' options."),
               matrixCalcAreaOption     = new Option("mxA", "matrixArea", true,
                    "(optional) id of calculation area for corresponding matrix option."),
               matrixTitleLangOption    = new Option("mxL", "matrixLang", true,
                    "Matrix titles language for row/column headers, to match with metadata 'title' for the corresponding band.\n" +
                    "Defaults to 'en'"),
               matrixDefaultOption = new Option("mxD", "matrixDefault", false,
                    "Default matrix (boolean no-argument option)"),

               nationalAreaTypeOption       = new Option("na", "nationalArea", true,
                   "National area type, may be given with multiple arguments."),
               nationalAreaPolygonOption    = new Option("naP", "nationalAreaPolygon", true,
                   "National area polygon file. Multi-valued, should match number of arguments to the '-na' option."),
               nationalAreaCountryISO       = new  Option("naC", "nationalAreaCountryISO", true,
                   "National area country code."),

               calcAreaPackageOption        = new Option("caF", "calcAreaFile", true,
                    "Path to GeoPackage file comprising calculation area polygons. See documentation for expected format and required internal attributes."),
               calcAreaNamePropertyOption   = new Option("caP", "calcAreaNameProperty", true,
                   "\"Name property\" to use for calculation area name ('carea_name' column) value in the GeoPackage file specified by '-caF'.\n" +
                       "The default is 'name'."),
               calcAreaDefaultOption        = new Option("caD", "calcAreaDefault", true,
                   "Default calculation area. Multi-valued option to correspond with area names present in " +
                           "'-caF' GeoPackage file, specifying default status of the corresponding area."),
               calcAreaAllDefaultOption     = new Option("caDA", "calcAreaAllDefault", false,
                   "Specify to set all calculation areas present in the GeoPackage file slated for import by the "+
                           "'-caF' option as default for the target baseline."),

               csvDelimOption    = new Option("csvS", "delimiter", true, "Column delimiter character for CSV files (defaults to ',')."),
               csvNewLineOption  = new Option("csvN", "newline", true, "Row delimiter character for CSV files (newline)."),

               newBaselineName  = new Option("bvN", "baselineVersionName", true,
                   "Baseline version name, required for \"new baseline\" invocations ('-n'). Must be unique."),
               newBaselineLocale = new Option("bvL", "baselineVersionLocale", true,
                   "Baseline version locale as ISO 639-1 code, used in conjunction with ('-n'). Defaults to 'en'."),
               newBaselineDesc  = new Option("bvD", "baselineVersionDesc", true,
                   "Baseline version description, used in conjunction with ('-n'). Optional."),
               newBaselineValidDate = new Option("bvV", "baselineVersionDate", true,
                   "Baseline version \"valid from\"-date as ISO 8601 (YYYY-MM-DD). Defaults to present day, according to the host system date."),
               newBaselineEcoPath = new Option("bvpE", "baselineEcoPath", true,
                   "Baseline version ecosystems GeoTIFF path, required for \"new baseline\" invocations ('-n')."),
               newBaselinePressurePath = new Option("bvpP", "baselinePressurePath", true,
                   "Baseline version pressures GeoTIFF path, required for \"new baseline\" invocations ('-n').");

        Option[] multiValuedOptions = new Option[] {
            metadataOption, mdLanguageOption,
            matrixOption, matrixNameOption, matrixTitleLangOption, matrixCalcAreaOption, matrixDefaultOption,
            nationalAreaTypeOption, nationalAreaPolygonOption, nationalAreaCountryISO,
            calcAreaDefaultOption
        };

        updateOption.setOptionalArg(true);

        dbOption.setRequired(true);
        dbPwOption.setRequired(true);
        dbuOption.setRequired(true);

        for (Option option : multiValuedOptions) {
            option.setArgs(Option.UNLIMITED_VALUES);
            option.setValueSeparator(',');
        }

        updateOption.setType(UpdateMode.class);
        updateOption.setConverter(new UpdateModeConverter());

        matrixDefaultOption.setType(Boolean.class);
        matrixDefaultOption.setConverter(new BooleanYesNoConverter("default matrix"));

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

        options.addOption(updateOption); options.addOption(baselineVOption);
        options.addOption(metadataOption); options.addOption(mdLanguageOption);

        options.addOption(nationalAreaTypeOption);  options.addOption(nationalAreaPolygonOption);
        options.addOption(nationalAreaCountryISO);

        options.addOption(matrixOption); options.addOption(matrixNameOption); options.addOption(matrixTitleLangOption);
        options.addOption(matrixCalcAreaOption); options.addOption(matrixDefaultOption);

        options.addOption(calcAreaPackageOption); options.addOption(calcAreaNamePropertyOption);
        options.addOption(calcAreaDefaultOption); options.addOption(calcAreaAllDefaultOption);

        options.addOption(csvDelimOption); options.addOption(csvNewLineOption);

        options.addOption(newBaselineName); options.addOption(newBaselineDesc); options.addOption(newBaselineValidDate);
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

    public SymphonySetup(String[] args) {
        try {
            if (Arrays.stream(args).anyMatch(arg -> arg.equals("--help") || arg.equals("-h"))) {
                HelpFormatter formatter = HelpFormatter.builder().get();
                formatter.printHelp("symphony-setup-tool", USAGE_HEADER, options, USAGE_FOOTER, true);
                System.exit(0);
            }

            setupCmd = new DefaultParser().parse(options, args);
            execute();
        } catch (ParseException | IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private void checkNewBaselineInvocation() throws ParseException, SQLException {
        if (RequiredNewBaselineOptionAliases.stream().allMatch(setupCmd::hasOption)) {
            String baselineVersionName =  setupCmd.getOptionValue("bvN");
            Integer bvId = db.baselineVersionIdByName(baselineVersionName);
            Integer optBvId = Util.tryParseInt(setupCmd.getOptionValue("bv"));
            if (optBvId != null) {
                throw new ParseException(String.format(
                    "Error: ambiguos invocation.%n-n and -bv options cannot be issued at the same time.")
                );
            }

            if (bvId != null) {
                throw new ParseException(
                    String.format("Error: The provided baseline version name '%s' already exists.%nIts id in the database is: %d",
                        baselineVersionName, bvId));
            }

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
            .filter(o -> !o.isRequired())
            .map(Option::getKey).toArray(String[]::new);
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
                }

                if (setupCmd.hasOption("n")) {
                    updateMode = UpdateMode.UPDATE;

                    Scanner prompt = new Scanner(System.in);
                    String pendingNewBaselineName = setupCmd.getOptionValue("bvN");

                    checkNewBaselineInvocation();
                    System.out.println(String.format("Pending baseline version installation: %s", pendingNewBaselineName));
                    System.out.println(String.format("---------------------------------------%s", "-".repeat(
                        pendingNewBaselineName.length())));

                    System.out.println("\nProceed with the import? ('y' to confirm)");
                    System.out.print("> ");

                    if (!prompt.nextLine().trim().equalsIgnoreCase("y")) {
                        System.out.println("Baseline version installation aborted interactively.");
                        return;
                    }

                    selectedBaselineVersion = db.getBaselineVersion(importNewBaselineVersion());
                } else {
                    throw new ParseException("...");
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

            System.out.println(String.format("Pending national areas import: %s", areaIdentifiers));
            System.out.println(String.format("-------------------------------%s", "-".repeat(
                areaIdentifiers.length())));

            System.out.println("\nProceed with the import? ('y' to confirm)");
            System.out.print("> ");

            if (!prompt.nextLine().trim().equalsIgnoreCase("y")) {
                System.out.println("National areas import aborted interactively.");
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

    private void execute() throws ParseException {
        db = getDb();

        if (setupCmd.hasOption("v") && !setupCmd.hasOption("s")) {
            throw new ParseException("'Verbose report' option is only valid in combination with -s/--status.");
        }

        // 'f' = Configuration file option passed (path)
        if (setupCmd.hasOption("f")) {
            int nonMandatory = Arrays.stream(setupCmd.getOptions())
                                    .filter(o -> !o.isRequired()).toList().size();
            if (nonMandatory > 1) {
                throw new ParseException("ERROR: When passing the 'file' input argument, other switches are disallowed");
            }

            throw new ParseException("File-based import is not yet implemented");

        } else if (setupCmd.hasOption("s")) {
            // 's' = Status option - print state of baseline
            try {
                setBaselineVersion();
                if (selectedBaselineVersion == null) return;

                Baseline baselineToReport = db.getBaselineForReport(this.currentBaselineVersion.getId());
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
        Baseline selectedBaseline = getDb().getBaseline(selectedBaselineVersion.getId());

        if (selectedBaseline.isMetaIncomplete()) {
            throw new ParseException("Matrix import is not possible for baseline version " +
                                     "with incomplete meta band coverage");
        }

        options.getOption("mxA").setConverter(
            new MatrixCalcAreaConverter(
                getDb().getAvailableCalculationAreaIds(),
                setupCmd.getOptionValues("caN"))
            );

        String[] matrixNames, matrixFiles;
        CalcAreaOption[] matrixAreas = setupCmd.getParsedOptionValues("mxA");

        matrixAreas = matrixAreas == null ? new CalcAreaOption[0] : matrixAreas;

        matrixFiles = setupCmd.getOptionValues("mx");
        matrixNames = setupCmd.getOptionValues("mxN");

        boolean[] matrixDefault = new boolean[matrixFiles.length];

        if (setupCmd.hasOption("mxD")) {
            Boolean[] _matrixDefault = setupCmd.getParsedOptionValues("mxD");

            for (int i = 0; i < _matrixDefault.length; ++i) {
                matrixDefault[i] = _matrixDefault[i];
            }
        }

        if (!(setupCmd.hasOption("mxN"))) {
            throw new ParseException("Matrix import: Matrix name ('-mxN') must be specified");
        }

        if (matrixFiles.length > matrixNames.length) {
            throw new ParseException(
                String.format("Too few matrix names: %d expected, %d provided",
                              matrixFiles.length, matrixNames.length)
            );
        }

        int lastDefaultMatrixIndex = ArrayUtils.lastIndexOf(matrixDefault, true);

        if (lastDefaultMatrixIndex > -1) {
            if (!setupCmd.hasOption("mxA") ||
                setupCmd.getOptionValues("mxA").length < lastDefaultMatrixIndex + 1) {
                throw new ParseException("Matrix import: Calculation area ('-mxA') must be specified " +
                                         "for a default matrix");
            }
        }

        List<MatrixImportSettings> matricesToImport =
            processSettings("mx", "mxL", MatrixImportSettings.class);

        for (int i = 0; i < matricesToImport.size(); ++i) {
            MatrixBase mx;

            MatrixImportSettings mxSetting = matricesToImport.get(i);
            mxSetting.setMatrixName(matrixNames[i]);
            mxSetting.setAreaId(matrixAreas.length > i
                                ? matrixAreas[i].getExistingId()
                                : null);

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
                    setupCmd.getOptionValues("caD"))
            );

        if (calcAreaProcedure.confirmImport()) {
            db.importCalculationAreas(calcAreaProcedure.areas);
        }
    }

    private int importNewBaselineVersion() throws ParseException, SQLException {
        String bvDescription = setupCmd.hasOption("bvD") ? setupCmd.getOptionValue("bvD") : "",
            bvLocale = setupCmd.hasOption("bvL") ? setupCmd.getOptionValue("bvL") : "en";
        LocalDate bvValidDate = null;

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

        for (Option rasterOption: new Option[]{ options.getOption("bvpE"), options.getOption("bvpP") }) {
            String rasterFilePathValue = setupCmd.getOptionValue(rasterOption);
            Path rasterFilePath = Path.of(rasterFilePathValue).normalize();

            if (!Files.exists(rasterFilePath)) {
                throw new ParseException(String.format("Raster file (%s) not found", rasterFilePathValue));
            }

            if (!Files.isReadable(rasterFilePath)) {
                throw new ParseException(String.format("Raster file (%s) not readable", rasterFilePathValue));
            }

            if (!(findFormat(new File(rasterFilePathValue)) instanceof GeoTiffFormat)) {
                throw new ParseException(String.format("File (%s) is not a valid GeoTiff raster", rasterFilePathValue));
            }
        }

        if (!ISO_LANG.contains(bvLocale)) {
            throw new ParseException(
                String.format("The provided baseline locale ('%s'), is not a valid ISO 639-1 language code.", bvLocale));
        }

        BaselineVersion baselineVersionToInstall = new BaselineVersion(
            setupCmd.getOptionValue("bvN"),
            bvDescription,
            bvValidDate,
            setupCmd.getOptionValue("bvpE"),
            setupCmd.getOptionValue("bvpP"),
            bvLocale
        );

        return db.insertBaselineVersion(baselineVersionToInstall);
    }

    private DbInterface getDb() {
        return db = new DbInterface(
            setupCmd.getOptionValue("db"), setupCmd.getOptionValue("dbU"), setupCmd.getOptionValue("dbP"),
            setupCmd.getOptionValue("dbS"), setupCmd.getOptionValue("dbPt"), setupCmd.getOptionValue("dbH"));
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
                String encoding = System.getProperty("file.encoding");
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
        UPDATE, REPLACE
    }
}
