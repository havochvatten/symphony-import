package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.database.DbTestInterface;
import se.havochvatten.symphonyconfig.setup.model.NationalArea;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Negative-path coverage for the file-based import mode.
 */
public class FileBasedConfigValidationTest extends CliTestBase {

    private static final String MINIMAL_CONFIG = RESOURCES_PATH + "import/baseline-minimal.yaml";

    public FileBasedConfigValidationTest() {
        super(false);
    }

    @Test
    void statusOptionIsRejectedAlongsideConfigFile() {
        String[] args = testCaseArgs("-f", MINIMAL_CONFIG, "-s");

        queueInteraction(() -> new SymphonySetup(args), "y");

        assertTrue(displaceErr.toString().contains("disallowed"),
            "Combining -f with -s must be rejected, not silently ignored. stderr was: "
                + displaceErr);

        assertNull(getDbInterface().getBaselineVersionByName("minimal-baseline"),
            "A rejected invocation must not install a baseline version");
    }

    @Test
    void geoTiffOverridesAreAllowedAlongsideConfigFile() {
        String[] args = testCaseArgs("-f", MINIMAL_CONFIG,
            "-bvpE", TEST_TIFF_E_PATH, "-bvpP", TEST_TIFF_P_PATH);

        queueInteraction(() -> new SymphonySetup(args), "y");

        assertTrue(!displaceErr.toString().contains("disallowed"),
            "-bvpE and -bvpP are the documented exceptions and must remain allowed. stderr was: "
                + displaceErr);

        assertNotNull(getDbInterface().getBaselineVersionByName("minimal-baseline"),
            "The -bvpE/-bvpP overrides are allowed with -f, so the import must actually run");
    }

    @Test
    void missingNationalAreaFileIsRejectedBeforeAnyRowIsDeleted() throws Exception {
        // Establish a good import first
        queueInteraction(() -> new SymphonySetup(
            testCaseArgs("-f", RESOURCES_PATH + "import/national-areas-import.yaml")), "y");

        List<NationalArea> before = getDbInterface().query(
            DbTestInterface.getAllNatAreasForCountryCodeQuery(dbSchema), NationalArea.handler, "SWE");
        assertEquals(3, before.size(), "Precondition: BOUNDARY, TEST and TYPES rows present");

        String cfg = writeConfig("natareas-typo.yaml",
            "operation: nationalAreas",
            "nationalAreas:",
            "  - type: BOUNDARY",
            "    file: " + absoluteResourcePath("/import/national-area/test-national-boundary.json"),
            "    countryISO: SWE",
            "  - type: TEST",
            "    file: /nonexistent/does-not-exist.json",
            "    countryISO: SWE");

        assertRejected(cfg, "nationalAreas[1].file");

        List<NationalArea> after = getDbInterface().query(
            DbTestInterface.getAllNatAreasForCountryCodeQuery(dbSchema), NationalArea.handler, "SWE");
        assertEquals(3, after.size(),
            "A config referencing a missing file must be rejected before any row is deleted");
    }

    @Test
    void nationalAreasWithoutBoundaryIsRejected() {
        String cfg = writeConfig("natareas-no-boundary.yaml",
            "operation: nationalAreas",
            "nationalAreas:",
            "  - type: COUNTY",
            "    file: " + absoluteResourcePath("/import/national-area/test-national-selectable.json"),
            "    countryISO: SWE");

        assertRejected(cfg, "exactly one BOUNDARY");
    }

    @Test
    void nationalAreasWithTwoBoundariesIsRejected() {
        String boundary = absoluteResourcePath("/import/national-area/test-national-boundary.json");
        String cfg = writeConfig("natareas-two-boundaries.yaml",
            "operation: nationalAreas",
            "nationalAreas:",
            "  - type: BOUNDARY",
            "    file: " + boundary,
            "    countryISO: SWE",
            "  - type: BOUNDARY",
            "    file: " + boundary,
            "    countryISO: SWE");

        assertRejected(cfg, "exactly one BOUNDARY");
    }

    @Test
    void allDefaultCombinedWithDefaultAreasIsRejected() {
        String cfg = writeConfig("areas-both-default.yaml",
            "operation: update",
            "baseline:",
            "  id: 1",
            "calculationAreas:",
            "  file: " + absoluteResourcePath("/import/calcarea-package.gpkg"),
            "  allDefault: true",
            "  defaultAreas:",
            "    - test-calc-area-1");

        assertRejected(cfg, "mutually exclusive");
    }

    @Test
    void sectionThatDoesNotApplyToTheOperationIsRejected() {
        String cfg = writeConfig("natareas-with-metadata.yaml",
            "operation: nationalAreas",
            "nationalAreas:",
            "  - type: BOUNDARY",
            "    file: " + absoluteResourcePath("/import/national-area/test-national-boundary.json"),
            "    countryISO: SWE",
            "metadata:",
            "  - file: " + absoluteResourcePath("/import/metadata-wellformed-complete-en.csv"),
            "    language: en");

        assertRejected(cfg, "not applicable");
    }

    @Test
    void newBaselineWithoutNameIsRejected() {
        String cfg = writeConfig("baseline-no-name.yaml",
            "operation: newBaseline",
            "baseline:",
            "  ecoPath: " + TEST_TIFF_E_PATH,
            "  pressurePath: " + TEST_TIFF_P_PATH);

        assertRejected(cfg, "baseline.name");
    }

    @Test
    void missingMetadataFileIsRejectedWithConfigShapedMessage() {
        String cfg = writeConfig("metadata-missing-file.yaml",
            "operation: update",
            "baseline:",
            "  id: 1",
            "metadata:",
            "  - file: /nonexistent/metadata.csv",
            "    language: en");

        assertRejected(cfg, "metadata[0].file");
        // The old behaviour told a file-mode user to pass '-md', a switch file mode forbids
        assertTrue(!displaceErr.toString().contains("-md"),
            "A file-mode error must not instruct the user to pass CLI switches");
    }

    @Test
    void unsupportedFileExtensionIsRejected() {
        String cfg = writeConfig("config.txt", "operation: newBaseline");
        assertRejected(cfg, "Unsupported configuration file format");
    }

    @Test
    void csvSettingsUnderNationalAreasIsRejected() {
        String cfg = writeConfig("natareas-with-csvsettings.yaml",
            "operation: nationalAreas",
            "nationalAreas:",
            "  - type: BOUNDARY",
            "    file: " + absoluteResourcePath("/import/national-area/test-national-boundary.json"),
            "    countryISO: SWE",
            "csvSettings:",
            "  delimiter: \";\"");

        assertRejected(cfg, "Section 'csvSettings' is not applicable");
    }

    @Test
    void multiCharacterCsvDelimiterIsRejected() {
        String cfg = writeConfig("csv-multichar-delimiter.yaml",
            "operation: update",
            "baseline:",
            "  id: 1",
            "metadata:",
            "  - file: " + absoluteResourcePath("/import/metadata-wellformed-complete-en.csv"),
            "    language: en",
            "csvSettings:",
            "  delimiter: \"::\"");

        assertRejected(cfg, "'csvSettings.delimiter' must be a single character");
    }

    @Test
    void nationalAreaEntryWithoutTypeIsReportedAsAMissingType() {
        // The BOUNDARY tally must not pre-empt the per-entry required-field checks
        String cfg = writeConfig("natareas-missing-type.yaml",
            "operation: nationalAreas",
            "nationalAreas:",
            "  - file: " + absoluteResourcePath("/import/national-area/test-national-boundary.json"),
            "    countryISO: SWE");

        assertRejected(cfg, "'nationalAreas[0].type' is required");
    }

    @Test
    void secondBaselineOnTheSameDayIsRejected() {
        String first = writeConfig("same-day-a.yaml",
            "operation: newBaseline",
            "baseline:",
            "  name: same-day-a",
            "  validFrom: \"2031-02-02\"",
            "  ecoPath: " + TEST_TIFF_E_PATH,
            "  pressurePath: " + TEST_TIFF_P_PATH);

        String second = writeConfig("same-day-b.yaml",
            "operation: newBaseline",
            "baseline:",
            "  name: same-day-b",
            "  validFrom: \"2031-02-02\"",
            "  ecoPath: " + TEST_TIFF_E_PATH,
            "  pressurePath: " + TEST_TIFF_P_PATH);

        try {
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", first)), "y");
            assertNotNull(getDbInterface().getBaselineVersionByName("same-day-a"),
                "Precondition: the first baseline installs");

            assertRejected(second, "validFrom");

            assertNull(getDbInterface().getBaselineVersionByName("same-day-b"),
                "A second baseline sharing validFrom must not be installed: the application "
                    + "throws BASELINE_VERSION_MULT_MATCHES when resolving the current baseline");
        } finally {
            for (String name : new String[]{"same-day-a", "same-day-b"}) {
                Integer id = getDbInterface().getBaselineVersionByName(name);
                if (id != null) {
                    getDbInterface().cleanBaselineVersion(id);
                }
            }
        }
    }

    @Test
    void operationValueIsCaseInsensitive() {
        String cfg = writeConfig("lowercase-op.yaml",
            "operation: newbaseline",
            "baseline:",
            "  name: lowercase-op-test",
            "  validFrom: \"2029-03-03\"",
            "  ecoPath: " + TEST_TIFF_E_PATH,
            "  pressurePath: " + TEST_TIFF_P_PATH);

        try {
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "y");
            assertNotNull(getDbInterface().getBaselineVersionByName("lowercase-op-test"),
                "'newbaseline' should be accepted as 'newBaseline'");
        } finally {
            Integer id = getDbInterface().getBaselineVersionByName("lowercase-op-test");
            if (id != null) {
                getDbInterface().cleanBaselineVersion(id);
            }
        }
    }

    @Test
    void unknownOperationGivesAReadableError() {
        String cfg = writeConfig("bad-op.yaml", "operation: instalEverything");

        assertRejected(cfg, "Must be one of");
        assertTrue(!displaceErr.toString().contains("InvalidFormatException"),
            "The operator should not see a raw Jackson exception name");
    }

    @Test
    void configFilePathIsNotAUserFacingKey() {
        String cfg = writeConfig("hijack.yaml",
            "operation: newBaseline",
            "configFilePath: /tmp",
            "baseline:",
            "  name: hijack-test");

        assertRejected(cfg, "configFilePath");
    }

    @Test
    void misspelledFieldIsRejected() {
        String cfg = writeConfig("typo-field.yaml",
            "operation: newBaseline",
            "baseline:",
            "  name: typo-field-test",
            "metdata:",
            "  - file: whatever.csv");

        assertRejected(cfg, "Unrecognized field \"metdata\"");
    }

    @Test
    void absolutePathsAreHonoured() {
        // Every other fixture exercises relative resolution; this one pins absolute paths
        String cfg = writeConfig("absolute-paths.yaml",
            "operation: newBaseline",
            "baseline:",
            "  name: absolute-path-test",
            "  validFrom: \"2029-05-05\"",
            "  ecoPath: " + TEST_TIFF_E_PATH,
            "  pressurePath: " + TEST_TIFF_P_PATH,
            "metadata:",
            "  - file: " + absoluteResourcePath("/import/metadata-wellformed-complete-en.csv"),
            "    language: en");

        try {
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "y", "y");
            assertNotNull(getDbInterface().getBaselineVersionByName("absolute-path-test"));
        } finally {
            Integer id = getDbInterface().getBaselineVersionByName("absolute-path-test");
            if (id != null) {
                getDbInterface().cleanBaselineVersion(id);
            }
        }
    }

    private String writeConfig(String name, String... lines) {
        try {
            Path dir = Path.of("target/test-resources");
            Files.createDirectories(dir);
            Path p = dir.resolve(name);
            Files.writeString(p, String.join(NEW_LINE, lines));
            return p.toString();
        } catch (IOException e) {
            fail("Could not write config: " + e.getMessage());
            return null;
        }
    }

    private void assertRejected(String configPath, String expectedFragment) {
        queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", configPath)), "y");
        assertTrue(displaceErr.toString().contains(expectedFragment),
            "Expected rejection mentioning '" + expectedFragment + "'. stderr was: " + displaceErr);
    }

    @AfterEach
    void removeInstalledBaseline() {
        Integer id = getDbInterface().getBaselineVersionByName("minimal-baseline");
        if (id != null) {
            getDbInterface().cleanBaselineVersion(id);
        }
        getDbInterface().cleanNationalAreas();
    }
}
