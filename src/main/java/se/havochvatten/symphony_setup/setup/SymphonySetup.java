package se.havochvatten.symphony_setup.setup;

import org.apache.commons.cli.*;
import org.apache.commons.lang3.ArrayUtils;
import se.havochvatten.symphony_setup.setup.config.CSVSettings;
import se.havochvatten.symphony_setup.setup.config.MatrixImportSettings;
import se.havochvatten.symphony_setup.setup.config.MetadataImportSettings;
import se.havochvatten.symphony_setup.setup.config.SettingsBase;
import se.havochvatten.symphony_setup.setup.database.DbInterface;
import se.havochvatten.symphony_setup.setup.model.Baseline;
import se.havochvatten.symphony_setup.setup.model.BaselineVersion;
import se.havochvatten.symphony_setup.setup.model.converter.DefaultMatrixArgConverter;
import se.havochvatten.symphony_setup.setup.model.converter.UpdateModeConverter;
import se.havochvatten.symphony_setup.setup.process.*;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class SymphonySetup {

    private CommandLine setupCmd;
    private DbInterface db;
    private BaselineVersion selectedBaselineVersion;
    private BaselineVersion currentBaselineVersion;

    public static final Options options = new Options();

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

               csvDelimOption    = new Option("csvS", "delimiter", true, "description"),
               csvNewLineOption  = new Option("csvN", "newline", true, "description");

        Option[] multiValuedOptions = new Option[] {
            metadataOption, mdLanguageOption,
            matrixOption, matrixNameOption, matrixTitleLangOption, matrixCalcAreaOption, matrixDefaultOption
        };

        newBaselineOption.setOptionalArg(true);
        updateOption.setOptionalArg(true);

        dbOption.setRequired(true);
        dbPwOption.setRequired(true);
        dbuOption.setRequired(true);

        for (Option option : multiValuedOptions) {
            option.setArgs(Option.UNLIMITED_VALUES);
        }

        updateOption.setType(UpdateMode.class);
        updateOption.setConverter(new UpdateModeConverter());

        matrixDefaultOption.setType(Boolean.class);
        matrixDefaultOption.setConverter(new DefaultMatrixArgConverter());

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

        options.addOption(matrixOption); options.addOption(matrixNameOption); options.addOption(matrixTitleLangOption);
        options.addOption(matrixCalcAreaOption); options.addOption(matrixDefaultOption);

        options.addOption(csvDelimOption); options.addOption(csvNewLineOption);
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
    // WIP
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

        } else {

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
                            throw new ParseException("No baseline version is present in this database.\n" +
                                "Use the -n option to install compliant baseline data");
                        }

                        selectedBaselineVersion = getBaselineVersion();

                        if (selectedBaselineVersion == null) {
                            // Process was aborted by user interaction
                            return;
                        }
                    }

                    if (setupCmd.hasOption("n")) {
                        // updateMode = UpdateMode.UPDATE;
                        throw new ParseException("Install baseline version is not yet implemented");
                    }

                    if (setupCmd.hasOption("md")) {
                        importMetadata();
                    }

                    if (setupCmd.hasOption("mx")) {
                        importSensitivityMatrix();
                    }

                } catch (Exception e) {
                    throw new ParseException(e.getMessage());
                }
            }
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

    private <T extends SettingsBase> List<T> processSettings(String opt, String langOpt, Class<T> settingsType)
        throws Exception {
        List<T> settings = new ArrayList<>();
        String[] files = setupCmd.getOptionValues(opt);
        String[] languageParams = setupCmd.getOptionValues(langOpt);
        String currentDefaultLang = selectedBaselineVersion.getLocale();

        for (int i = 0; i < files.length; ++i) {
            boolean hasLang = languageParams != null && languageParams.length > i;

            T settingObj = SettingsBase.create(settingsType, selectedBaselineVersion, files[i],
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

        String[] matrixNames, matrixAreas, matrixFiles,
                 _matrixAreas = setupCmd.getOptionValues("mxA");
        boolean[] matrixDefault;

        matrixAreas = _matrixAreas == null ? new String[0] : _matrixAreas;

        matrixFiles = setupCmd.getOptionValues("mx");
        matrixNames = setupCmd.getOptionValues("mxN");

        matrixDefault = new boolean[matrixFiles.length];

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
                                ? Integer.parseInt(matrixAreas[i])
                                : null);

            switch (mxSetting.format) {
                case CSV -> {
                    mx = new MatrixCsv(mxSetting, selectedBaseline, getCSVSettings());
                }
                case ODS -> throw new ParseException("Sensitivity matrix as ODS not implemented");
                case XLSX -> throw new ParseException("");
                default -> throw new ParseException("Unknown sensitivity matrix file format");
            }

            db.updateMatrix(mx);
        }
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
