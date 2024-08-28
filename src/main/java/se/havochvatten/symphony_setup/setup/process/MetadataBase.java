package se.havochvatten.symphony_setup.setup.process;

import org.apache.commons.cli.ParseException;
import se.havochvatten.symphony_setup.setup.config.MetadataImportSettings;
import se.havochvatten.symphony_setup.setup.model.IValidatedProcedure;
import se.havochvatten.symphony_setup.setup.model.ProcedureBase;
import se.havochvatten.symphony_setup.setup.model.SymphonyBand;
import se.havochvatten.symphony_setup.setup.model.SymphonyCategory;

import java.util.*;

import static se.havochvatten.symphony_setup.setup.SymphonySetup.Util.parseNullableBoolean;

public abstract class MetadataBase extends ProcedureBase implements IValidatedProcedure {
    protected static final String SYMPHONY_CATEGORY = "symphonycategory";
    protected static final String BANDNUMBER = "bandnumber";
    protected static final String SYMPHONY_THEME = "symphonytheme";
    protected static final String TITLE = "title";
    protected static final String DEFAULT_SELECTED = "default_selected";
    static final String Ecosystem = SymphonyCategory.ECOSYSTEM.getDbVal();
    static final String Pressure  = SymphonyCategory.PRESSURE.getDbVal();

    protected static final String[] reqFields = new String[]{ BANDNUMBER, SYMPHONY_CATEGORY, TITLE };

    public final MetadataImportSettings settings;
    public final Map<SymphonyCategory, List<SymphonyBand>> bands =
        Map.of( SymphonyCategory.ECOSYSTEM, new ArrayList<>(),
                SymphonyCategory.PRESSURE,  new ArrayList<>());
    protected final Map<String, Integer> fieldsToColumns = new HashMap<>();

    protected SymphonyCategory getCategory(String _category) {
        try {
            return SymphonyCategory.valueOf(_category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public MetadataBase(MetadataImportSettings settings) {
        this.settings = settings;
    }

    protected void process() throws ParseException {
        try {
            if (!validate()) {
                throw new ParseException(errorMessage());
            }

            if (!collectBands()) {
                throw new ParseException(errorMessage());
            }
        } catch (Exception e) {
            throw new ParseException(e.getMessage());
        }
    }

    protected boolean validateFieldSet(Set<String> allFields) {
        String[] missing = Arrays.stream(reqFields).filter(s -> !allFields.contains(s))
            .toArray(String[]::new);
        if (missing.length > 0) {
            validationErrors.add(String.format("Missing required column%s: %s",
                missing.length > 1 ? "s" : "",
                String.join(", ", missing)));
            return false;
        }
        if (!allFields.contains(SYMPHONY_THEME)) {
            validationMessages.add(String.format("Missing '%1$s' column!\n" +
                "This is allowed, but be aware that bands that don't specify '%1$s' won't render in the default UI." +
                "For instances that will employ the default UI, make sure to supply '%1$s' before deployment",
                SYMPHONY_THEME));
        }
        return true;
    }

    protected boolean validateBand(Integer preBandNumber, String bnStr, SymphonyCategory c, String catStr) {
        if(c == null || preBandNumber == null) {
            if (preBandNumber == null) {
                validationErrors.add(
                    String.format("All values in column « bandnumber » must be numeric.\n" +
                        "('%s' was encountered.)", bnStr));
            }

            if (c == null) {
                validationErrors.add(
                    String.format("Invalid value (\"%s\") encountered in column « symphonycategory ».\n" +
                            "The allowed category values are, exclusively: '%s' and '%s' (case-insensitive).\n",
                        catStr, SymphonyCategory.ECOSYSTEM.getDbVal(), SymphonyCategory.PRESSURE.getDbVal()));
            }

            validationErrors.add("Make sure the input metadata table is sound.");
            return false;
        }

        int bandCount =
            c == SymphonyCategory.ECOSYSTEM ?
                settings.ecosystemBandsCount : settings.pressureBandsCount;

        // Note: Input index is 1-based, we needn't subtract from the total to compare
        if (preBandNumber > bandCount) {
            validationErrors.add(
                String.format("Band index (%d) exceeding number of actual bands (%d) in the baseline TIFF data " +
                    "for category '%s'", preBandNumber, bandCount, c.getDbVal()));
            return false;
        }

        return true;
    }

    protected SymphonyBand makeBand(int preBandNumber, String defaultSelected) {
        return new SymphonyBand(settings.language,
            // note 1-based index in input file
            preBandNumber - 1,
            parseNullableBoolean(defaultSelected));
    }

    public abstract boolean validate();
    public abstract boolean collectBands();

    public boolean confirmImport() {
        int importEBandsCount = bands.get(SymphonyCategory.ECOSYSTEM).size(),
            importPBandsCount = bands.get(SymphonyCategory.PRESSURE).size();

        boolean exECount = settings.ecosystemBandsCount < importEBandsCount,
                exPCount = settings.pressureBandsCount < importPBandsCount,
                partial  = settings.ecosystemBandsCount > importEBandsCount ||
                           settings.pressureBandsCount  > importPBandsCount;

        String eProportion = String.format("%d/%d", importEBandsCount, settings.ecosystemBandsCount),
               pProportion = String.format("%d/%d", importPBandsCount, settings.pressureBandsCount);

        if (exECount || exPCount) {
            String faultyComponentDesc;

            if (exECount && exPCount) {
                faultyComponentDesc =
                    String.format("for both categories (%s bands: %s | %s bands: %s)",
                                        Ecosystem, eProportion, Pressure, pProportion);
            } else {
                faultyComponentDesc =
                    String.format("for the '%s' category (%s)",
                    exECount ? Ecosystem   : Pressure,
                    exECount ? eProportion : pProportion);
            }

            System.out.println(
                String.format(
                    "The import file specifies too many bands %s. Aborting the import procedure.",
                    faultyComponentDesc));

            return false;
        }

        Scanner prompt = new Scanner(System.in);

        System.out.println(String.format("Pending metadata import: \"%s\"", settings.fileName()));
        System.out.println(String.format("---------------------------%s", "-".repeat(settings.fileName().length())));

        if (partial) {
            System.out.println(
                "Note: the provided metadata table is _partial_: only a subset of all bands defined in the GeoTIFF" +
                "files are included.");
            System.out.println(
                String.format("%s bands: %s | %s bands: %s",
                              Ecosystem, eProportion, Pressure, pProportion));
        }
        System.out.println("\nProceed with the import? ('y' to confirm)");
        System.out.print("> ");

        return prompt.nextLine().trim().equalsIgnoreCase("y");
    }
}
