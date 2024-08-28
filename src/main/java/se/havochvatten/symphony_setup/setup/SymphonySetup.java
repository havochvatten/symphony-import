package se.havochvatten.symphony_setup.setup;

import org.apache.commons.cli.*;
import org.apache.commons.lang3.ArrayUtils;
import se.havochvatten.symphony_setup.setup.config.MetadataImportSettings;
import se.havochvatten.symphony_setup.setup.database.DbInterface;
import se.havochvatten.symphony_setup.setup.model.BaselineVersion;
import se.havochvatten.symphony_setup.setup.process.MetadataBase;
import se.havochvatten.symphony_setup.setup.process.MetadataCsv;
import se.havochvatten.symphony_setup.setup.process.MetadataXlsx;

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

               csvDelimOption    = new Option("csvS", "delimiter", true, "description"),
               csvNewLineOption  = new Option("csvN", "newline", true, "description");

        newBaselineOption.setOptionalArg(true);
        updateOption.setOptionalArg(true);

        dbOption.setRequired(true);
        dbPwOption.setRequired(true);
        dbuOption.setRequired(true);

        options.addOption(newBaselineOption);
        options.addOption(configFileOption);
        options.addOption(statusOption);
        options.addOption(helpOption);

        options.addOption(dbOption); options.addOption(dbuOption); options.addOption(dbPwOption);
        options.addOption(dbPtOption); options.addOption(dbSOption); options.addOption(dbHOption);

        options.addOption(updateOption); options.addOption(baselineVOption);
        options.addOption(metadataOption); options.addOption(mdLanguageOption);

        options.addOption(csvDelimOption); options.addOption(csvNewLineOption);
    }

    public static final Set<String> SYM_LANG = Set.of("en", "fr", "sv");
    public static final Set<String> ISO_LANG = Set.of(Locale.getISOLanguages());

    public static UpdateMode updateMode = UpdateMode.UPDATE;

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

        if (setupCmd.hasOption("f")) {
            int nonMandatory = Arrays.stream(setupCmd.getOptions())
                                    .filter(o -> !o.isRequired()).toList().size();
            if(nonMandatory > 1) {
                throw new ParseException("ERROR: When passing the 'file' input argument, other switches are disallowed");
            }

            throw new ParseException("File-based import is not yet implemented");

        } else {
            if (setupCmd.hasOption("s")) {
                throw new ParseException("Status option is not yet implemented");
            }

            if (setupCmd.hasOption("u")) {
                try {
                    updateMode = UpdateMode.parse(setupCmd.getOptionValue("u"));

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

                    if (setupCmd.hasOption("md")) {
                        importMetadata();
                    }

                    if (setupCmd.hasOption("mx")) {
                        throw new ParseException("Matrix import not implemented");
                    }
                } catch (Exception e) {
                    throw new ParseException(e.getMessage());
                }
            }

            if (setupCmd.hasOption("n")) {
                throw new ParseException("Install baseline version is not yet implemented");
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

    private void importMetadata() throws Exception {
        List<MetadataImportSettings> metadataToImport = new ArrayList<>();
        String[] mdFile = setupCmd.getOptionValues("md");
        String[] mdLanguageOpt = setupCmd.getOptionValues("mdL");
        String currentDefaultLang = selectedBaselineVersion.getLocale();

        for (int i = 0; i < mdFile.length; ++i) {
            MetadataImportSettings mdSettings;

            boolean clear = updateMode == UpdateMode.REPLACE;
            boolean hasLang = mdLanguageOpt != null && mdLanguageOpt.length > i;

            mdSettings = new MetadataImportSettings(selectedBaselineVersion, mdFile[i],
                hasLang ? mdLanguageOpt[i] : null,
                currentDefaultLang, clear, i);

            if (!mdSettings.validate()) {
                throw new ParseException(mdSettings.errorMessage());
            }

            metadataToImport.add(mdSettings);
            String parsingMessage = mdSettings.parsingMessage();

            if (parsingMessage != null) {
                System.out.println(parsingMessage);
            }

            currentDefaultLang = hasLang ? mdLanguageOpt[i] : currentDefaultLang;
        }

        for (MetadataImportSettings mdSettings : metadataToImport) {
            MetadataBase md;

            switch (mdSettings.format) {
                case CSV -> {
                    String sepValue = setupCmd.getOptionValue("csvS");
                    String nlValue = setupCmd.getOptionValue("csvN");
                    Character separator = sepValue == null ? null : sepValue.charAt(0);
                    String newLine =
                        nlValue == null || !nlValue.equalsIgnoreCase("windows") ?
                            null : "\r\n";

                    md = new MetadataCsv(mdSettings, separator, newLine);
                }
                case ODS -> throw new ParseException("Metadata as ODS not implemented");
                case XLSX -> md = new MetadataXlsx(mdSettings);
                default -> throw new ParseException("Unknown metadata file format");
            }

            db.updateMetadata(md);
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
        UPDATE, REPLACE;

        public static UpdateMode parse(String uValue) throws ParseException {

            if (uValue == null ||
                uValue.equalsIgnoreCase("update") ||
                uValue.equalsIgnoreCase("u"))
                return UpdateMode.UPDATE;

            if (uValue.equalsIgnoreCase("replace") ||
                uValue.equalsIgnoreCase("r"))
                return UpdateMode.REPLACE;

            throw new ParseException(
                "Unrecognized update mode (\"" + uValue + "\" given).\n" +
                "Supported update modes are `update` (default) and `replace` only.");
        }
    }
}
