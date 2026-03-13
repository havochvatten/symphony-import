package se.havochvatten.symphony_setup.setup.model;

import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.GridFormatFinder;
import org.geotools.util.factory.Hints;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static se.havochvatten.symphony_setup.setup.SymphonySetup.Util.*;

public class Baseline {

    public BaselineVersion version;
    public Map<SymphonyCategory, BaselineComponent> components;
    public final Map<SymphonyCategory, Integer> bandsCount = new EnumMap<>(SymphonyCategory.class);

    public List<Integer> allMatrixIds;
    public List<DbMatrix> defaultMatrices;
    public List<DbCalculationArea> defaultCalcAreas;

    private boolean metaIncomplete = false;
    private static final Hints g2h = new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE);

    public Baseline(BaselineVersion version) throws Exception {
        for(String path : version.tiffFilePaths().values()) {
            if (!new File(path).isFile()) {
                throw new Exception("File not found at path: " + path);
            }
        }
        components =
            Map.of(SymphonyCategory.ECOSYSTEM, new BaselineComponent(),
                   SymphonyCategory.PRESSURE, new BaselineComponent());

        this.version = version;
    }

    public static GridCoverage2D readTiff(String path) throws IOException {
        File file = new File(path);
        return GridFormatFinder.findFormat(file)
                .getReader(file, g2h).read(null);
    }

    // WIP
    public void collectComponents(SymphonyBand[] ecoBands, SymphonyBand[] pressureBands) throws Exception {
        SymphonyBand[][] bands = new SymphonyBand[][]{ ecoBands, pressureBands };

        for (Map.Entry<SymphonyCategory, String> pathEntry : version.tiffFilePaths().entrySet()) {
            SymphonyCategory category = pathEntry.getKey();
            int cOrdinal = category.ordinal();

            GridCoverage2D coverage = readTiff(pathEntry.getValue());
            bandsCount.put(category, coverage.getSampleDimensions().length);

            metaIncomplete |= bands[cOrdinal].length < coverage.getSampleDimensions().length;

            for (int ci = 0; ci < bands[cOrdinal].length; ++ci) {
                SymphonyBand band = bands[cOrdinal][ci];

                band.setDefaultLanguage(this.version.getLocale());
                components.get(category).bands.put(band.getBandNumber(), band);
            }
        }
    }

    // WIP
    public void collectBandMeta(SymphonyCategory category, MetaValue[] componentMeta) throws Exception {
        if (components.get(category) == null) {
            throw new ParseException("Unrecognized category"); // kan vara redundant
        }

        for (SymphonyBand band : components.get(category).bands.values()) {
            MetaValue[] bandMeta = Arrays.stream(componentMeta).filter(mv -> mv.getBandId() == band.getId())
                                    .toArray(MetaValue[]::new);
            for (MetaValue mv : bandMeta) {
                band.setMetaValue(mv.getLanguage(), mv);
            }
        }
    }

    public boolean isMetaIncomplete() {
        return metaIncomplete;
    }

    public BaselineVersion getVersion() {
        return version;
    }

    public Map<SymphonyCategory, BaselineComponent> getComponents() {
        return components;
    }

    public Collection<SymphonyBand> bandsByCategory(SymphonyCategory category) {
        return components.get(category).bands.values();
    }

    public List<Integer> getAllMatrixIds() {
        return allMatrixIds;
    }

    public void setAllMatrixIds(List<Integer> allMatrixIds) {
        this.allMatrixIds = allMatrixIds;
    }


    public List<DbCalculationArea> getDefaultCalcAreas() {
        return defaultCalcAreas;
    }

    public void setDefaultCalcAreas(List<DbCalculationArea> defaultCalcAreas) {
        this.defaultCalcAreas = defaultCalcAreas;
    }

    public List<DbMatrix> getDefaultMatrices() {
        return defaultMatrices;
    }

    public void setDefaultMatrices(List<DbMatrix> defaultMatrices) {
        this.defaultMatrices = defaultMatrices;
    }

    private void printVerboseMetaStatus() {
        for (SymphonyCategory category : SymphonyCategory.values()) {
            System.out.println(STATUS_SECTION_SEPARATOR);
            System.out.printf("Metadata details for %s rasters%n", category.getDbVal());
            System.out.println(STATUS_SECTION_DSEPARATOR);

            for (SymphonyBand band : components.get(category).bands.values()) {
                band.printMetaCountTable();
            }

            System.out.println();
        }
    }

    public void printStatusReport(boolean verbose) {
        String description = version.getDescription().isBlank() ? "[ not specified ]" : version.getDescription();

        int availableEcoBands = components.get(SymphonyCategory.ECOSYSTEM).bands.size();
        int availablePressureBands = components.get(SymphonyCategory.PRESSURE).bands.size();

        boolean ecoMetaComplete = availableEcoBands == bandsCount.get(SymphonyCategory.ECOSYSTEM);
        boolean presMetaComplete = availablePressureBands == bandsCount.get(SymphonyCategory.PRESSURE);

        System.out.printf("Baseline version id: %d%n", version.getId());
        System.out.printf("Baseline version name: %s%n", version.getName());
        System.out.printf("Baseline version description:%n%s%n%n", description);
        System.out.println(STATUS_SECTION_SEPARATOR);
        System.out.println("Metadata overall status");
        System.out.println(STATUS_SECTION_SEPARATOR);
        System.out.printf("Ecosystem bands (essential metadata provided / total): %d / %d    %s%n",
            availableEcoBands, bandsCount.get(SymphonyCategory.ECOSYSTEM),
            getValidIndicator(ecoMetaComplete, "(incomplete!)"));
        System.out.printf("Pressure bands  (essential metadata provided / total): %d / %d    %s%n",
            availablePressureBands, bandsCount.get(SymphonyCategory.PRESSURE),
            getValidIndicator(presMetaComplete, "(incomplete!)"));

        if (verbose) {
            printVerboseMetaStatus();
        }

        if (isMetaIncomplete()) {
            System.out.println("Metadata coverage is incomplete.");
        }

        System.out.println();
        System.out.println(STATUS_SECTION_SEPARATOR);
        System.out.println("Sensitivity matrix status");
        System.out.println(STATUS_SECTION_SEPARATOR);

        for (DbMatrix matrix : defaultMatrices) {
            System.out.printf("Sensitivity matrix id: %d%n", matrix.getId());
            System.out.printf("Sensitivity matrix name: %s%n", matrix.getName());
            if (matrix.missingEcoBands.size() + matrix.missingPressureBands.size() == 0) {
                System.out.println("All baseline data bands present in matrix");
            } else {
                if (!matrix.missingEcoBands.isEmpty()) {
                    System.out.printf(
                            "Sensitivity scores for these ecosystem band number(s) missing in matrix: %s%n",
                        StringUtils.join(matrix.missingEcoBands.stream().map(n -> n + 1).toList(), ", ")
                    );
                }
                if (!matrix.missingPressureBands.isEmpty()) {
                    System.out.printf(
                            "Sensitivity scores for these pressure band number(s) missing: %s%n",
                        StringUtils.join(matrix.missingPressureBands.stream().map(n -> n + 1).toList(), ", ")
                    );
                }
            }
            int ecoExpected = bandsCount.get(SymphonyCategory.ECOSYSTEM) - matrix.getMissingEcoBands().size();
            int presExpected = bandsCount.get(SymphonyCategory.PRESSURE) - matrix.getMissingPressureBands().size();

            if (matrix.getActualScoresCount() != matrix.getExpectedScoresCount()) {
                System.out.println("Corrupt matrix detected! It may produce errors at runtime if calculations are " +
                    "triggered for missing components.");
                System.out.printf("Actual number of sensitivity scores: %d%n", matrix.getActualScoresCount());
                System.out.printf("Expected number of sensitivity scores (%d x %d): %d%n",
                    ecoExpected, presExpected, matrix.getExpectedScoresCount());
            } else {
                System.out.printf("The matrix is complete with respect to the number of included components:%n" +
                        "(%d x %d) = %d sensitivity scores present.%n", ecoExpected, presExpected, matrix.getExpectedScoresCount());
            }
            System.out.println();
        }

        if (verbose) {
            System.out.printf(
                "Altogether, %d sensitivity matrices (possibly including custom scores) have been defined " +
                        "for this baseline version.%n", getAllMatrixIds().size());
            System.out.println();
        }

        System.out.println(STATUS_SECTION_SEPARATOR);
        System.out.println("Calculation area(s) status");
        System.out.println(STATUS_SECTION_SEPARATOR);

        for (DbCalculationArea area : getDefaultCalcAreas()) {
            System.out.printf("Calculation area id: %d%n", area.getId());
            System.out.printf("Calculation area name: %s%n", area.getName());
            defaultMatrices.stream().filter(m -> m.getId() == area.getDefaultSensitivityMatrixId())
                    .findFirst().ifPresent(matrix -> System.out.printf("Default sensitivity matrix: %s%n", matrix.getName()));
            System.out.println();
        }
    }
}
