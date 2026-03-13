package se.havochvatten.symphony_setup.setup;

import org.apache.commons.cli.*;
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
import se.havochvatten.symphony_setup.setup.config.CalcAreaImportSettings.*;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

import static org.geotools.coverage.grid.io.GridFormatFinder.findFormat;

public class SymphonySetup {

    private CommandLine setupCmd;
    private DbInterface db;
    private BaselineVersion selectedBaselineVersion;
    private BaselineVersion currentBaselineVersion;

    public static final Options options = new Options();

    public static final Set<String> NationalAreaOptionAliases;
    public static final Set<String> RequiredNewBaselineOptionAliases;

    static {
        Option newBaselineOption = new Option("n", "newBaseline", false, "description"),
               updateOption      = new Option("u", "update", true, "description"),
               configFileOption  = new Option("f", "file", true, "description"),
               statusOption      = new Option("s", "status", false, "description"),
               helpOption        = new Option("h", "help", false, "help"),

               dbOption          = new Option("db",   "database", true, "description"),
               dbuOption         = new Option("dbU",  "dbUser", true, "description"),
               dbPwOption        = new Option("dbP",  "dbPassword", true, "description"),
               dbPtOption         = new Option("dbPt", "dbPort", true, "description"),
               dbSOption         = new Option("dbS",  "dbSchema", true, "description"),
               dbHOption         = new Option("dbH",  "dbHost", true, "description"),

               baselineVOption   = new Option("bv",   "baselineVersion", true, "description"),
               metadataOption    = new Option("md",   "metadata", true, "description"),
               mdLanguageOption  = new Option("mdL",  "metadataLang", true, "description"),

               matrixOption             = new Option("mx", "matrix", true, "description"),
               matrixNameOption         = new Option("mxN", "matrixName", true, "description"),
               matrixCalcAreaOption     = new Option("mxA", "matrixArea", true, "description"),
               matrixTitleLangOption    = new Option("mxL", "matrixLang", true, "description"),
               matrixDefaultOption = new Option("mxD", "matrixDefault", false, "description"),

               nationalAreaTypeOption       = new Option("na", "nationalArea", true, "description"),
               nationalAreaPolygonOption    = new Option("naP", "nationalAreaPolygon", true, "description"),
               nationalAreaCountryISO       = new  Option("naC", "nationalAreaCountryISO", true, "description"),

               calcAreaNamePropertyOption   = new Option("caP", "calcAreaNameProperty", true, "description"),
               calcAreaPackageOption        = new Option("caF", "calcAreaFile", true, "description"),
               calcAreaDefaultOption        = new Option("caD", "calcAreaDefault", true, "description"),
               calcAreaAllDefaultOption     = new Option("caDA", "calcAreaAllDefault", false, "description"),

               csvDelimOption    = new Option("csvS", "delimiter", true, "description"),
               csvNewLineOption  = new Option("csvN", "newline", true, "description"),

               newBaselineLocale = new Option("bvL", "baselineVersionLocale", true, "description"),
               newBaselineName  = new Option("bvN", "baselineVersionName", true, "description"),
               newBaselineDesc  = new Option("bvD", "baselineVersionDesc", true, "description"),
               newBaselineValidDate = new Option("bvV", "baselineVersionDate", true, "description"),
               newBaselineEcoPath = new Option("bvpE", "baselineEcoPath", true, "description"),
               newBaselinePressurePath = new Option("bvpP", "baselinePressurePath", true, "description");

        Option[] multiValuedOptions = new Option[] {
            metadataOption, mdLanguageOption,
            matrixOption, matrixNameOption, matrixTitleLangOption, matrixCalcAreaOption, matrixDefaultOption,
            nationalAreaTypeOption, nationalAreaPolygonOption, nationalAreaCountryISO,
            calcAreaDefaultOption
        };

        newBaselineOption.setOptionalArg(true);
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
        options.addOption(statusOption);
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
            setupCmd = new DefaultParser().parse(options, args);
            execute();
        } catch (ParseException e) {
            System.err.println(e.getMessage());
        }
    }

    private boolean checkNewBaselineInvocation() throws ParseException, SQLException {
        if (RequiredNewBaselineOptionAliases.stream().allMatch(setupCmd::hasOption)) {
            String baselineVersionName =  setupCmd.getOptionValue("bvN");
            Integer bvId = db.baselineVersionIdByName(baselineVersionName);
            Integer optBvId = Util.tryParseInt(setupCmd.getOptionValue("bv"));
            if (optBvId != null) {
                throw new ParseException(String.format(
                    "Error: ambiguos invocation.%n-n and -bv options cannot be issued at the same time.")
                );
            }

            if (bvId == null) {
                return true;
            } else {
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

    private void importBaselineData() throws ParseException {
        // 's' = Status option - print state of baseline
        if (setupCmd.hasOption("s")) {
            throw new ParseException("Status option is not yet implemented");
        }

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

                    updateMode = setupCmd.getParsedOptionValue("u");

                    currentBaselineVersion = db.getBaselineVersion(null);

                    if (currentBaselineVersion == null) {
                        throw new ParseException("No baseline version is present in the target database.\n" +
                            "Use the -n option to install compliant baseline data");
                    }

                    selectedBaselineVersion = getBaselineVersion();

                    if (selectedBaselineVersion == null && setupCmd.hasOption("bv")) {
                        System.out.println("Baseline version with id " + setupCmd.getOptionValue("bv") +
                            " was not found in the target database. Aborting.");
                        return;
                    }
                }

                if (setupCmd.hasOption("n")) {
                    updateMode = UpdateMode.UPDATE;

                    Scanner prompt = new Scanner(System.in);
                    String pendingNewBaselineName = setupCmd.getOptionValue("bvN");

                    if (checkNewBaselineInvocation()) {
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

        // 'f' = Configuration file option passed (path)
        if (setupCmd.hasOption("f")) {
            int nonMandatory = Arrays.stream(setupCmd.getOptions())
                                    .filter(o -> !o.isRequired()).toList().size();
            if(nonMandatory > 1) {
                throw new ParseException("ERROR: When passing the 'file' input argument, other switches are disallowed");
            }

            throw new ParseException("File-based import is not yet implemented");

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
                String.format("Update procedure invoked without specifying baseline version id.\n\n" +
                    "You may:\n" +
                    "- [ Numeric input ]\tEnter an existing baseline version id to target.\n" +
                    "                   \tAvailable baseline version ids = (%s)\n" +
                    "- [   a / n / q   ]\tAbort update\n" +
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
                    System.out.println("Update aborted interactively.");
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
                case CSV -> {
                    md = new MetadataCsv(mdSettings, getCSVSettings());
                }
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
