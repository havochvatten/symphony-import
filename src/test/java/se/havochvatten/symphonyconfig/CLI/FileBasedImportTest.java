package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.model.Baseline;
import se.havochvatten.symphonyconfig.setup.model.BaselineVersion;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for importing baselines and national areas using configuration files.
 */
public class FileBasedImportTest extends CliTestBase {
    private static final String JSON_CONFIG_PATH = "src/test/resources/import/baseline-import-full.json";
    private static final String YAML_CONFIG_PATH = "src/test/resources/import/baseline-import-full.yaml";

    private static final String MINIMAL_CONFIG_PATH = "src/test/resources/import/baseline-minimal.yaml";
    private static final String NATIONAL_AREAS_CONFIG_PATH = "src/test/resources/import/national-areas-import.yaml";

    public FileBasedImportTest() {
        super(false);
    }

    @Test
    void importFullBaselineFromJSON() {
        // cli arguments
        // -f   [configuration file path]
        // -bvpE [override ecosystem GeoTIFF path]   // optional override
        // -bvpP [override pressure GeoTIFF path]    // optional override
        String[] args = testCaseArgs("-f", JSON_CONFIG_PATH,
                "-bvpE", TEST_TIFF_E_PATH,  // Override paths from config
                "-bvpP", TEST_TIFF_P_PATH);

        queueInteraction(() -> {
            new SymphonySetup(args);

            try {
                // Assert that the baseline was created
                Integer bvId = getDbInterface().baselineVersionIdByName(TEST_BASELINE_NAME);
                assertNotNull(bvId, "Baseline version should be created");
                this.bvId = bvId;

                // Assert the baseline has the expected bilingual metadata and matrix
                Baseline bl = getDbInterface().getBaseline(bvId);
                assertBilingualMatrixBaseline(bl);

            } catch (SQLException ex) {
                fail("Database error: " + ex.getMessage());
            }
        }, "y", "y", "y", "y", "y");  // 5 confirmations: baseline, 2 metadata, matrix, calc areas
    }

    @Test
    void importFullBaselineFromJSONWithRelativePaths() {
        // Test without overriding paths - let config use relative paths
        String[] args = testCaseArgs("-f", JSON_CONFIG_PATH);

        queueInteraction(() -> {
            new SymphonySetup(args);

            try {
                Integer bvId = getDbInterface().baselineVersionIdByName(TEST_BASELINE_NAME);
                assertNotNull(bvId, "Baseline version should be created with relative paths");
                this.bvId = bvId;

                Baseline bl = getDbInterface().getBaseline(bvId);
                assertBilingualMatrixBaseline(bl);

            } catch (SQLException ex) {
                fail("Database error: " + ex.getMessage());
            }
        }, "y", "y", "y", "y", "y");
    }

    @Test
    void importFullBaselineFromYAML() {
        // cli arguments
        // -f   [configuration file path]
        // -bvpE [override ecosystem GeoTIFF path]   // optional override
        // -bvpP [override pressure GeoTIFF path]    // optional override
        String[] args = testCaseArgs("-f", YAML_CONFIG_PATH,
                "-bvpE", TEST_TIFF_E_PATH,  // Override paths from config
                "-bvpP", TEST_TIFF_P_PATH);

        queueInteraction(() -> {
            new SymphonySetup(args);

            try {
                // Assert that the baseline was created
                Integer bvId = getDbInterface().baselineVersionIdByName(TEST_BASELINE_NAME);
                assertNotNull(bvId, "Baseline version should be created");
                this.bvId = bvId;

                // Assert the baseline has the expected bilingual metadata and matrix
                Baseline bl = getDbInterface().getBaseline(bvId);
                assertBilingualMatrixBaseline(bl);

            } catch (SQLException ex) {
                fail("Database error: " + ex.getMessage());
            }
        }, "y", "y", "y", "y", "y");  // 5 confirmations: baseline, 2 metadata, matrix, calc areas
    }

    @Test
    void importFullBaselineFromYAMLWithRelativePaths() {
        // Test without overriding paths - let config use relative paths
        String[] args = testCaseArgs("-f", YAML_CONFIG_PATH);

        queueInteraction(() -> {
            new SymphonySetup(args);

            try {
                Integer bvId = getDbInterface().baselineVersionIdByName(TEST_BASELINE_NAME);
                assertNotNull(bvId, "Baseline version should be created with relative paths");
                this.bvId = bvId;

                Baseline bl = getDbInterface().getBaseline(bvId);
                assertBilingualMatrixBaseline(bl);

            } catch (SQLException ex) {
                fail("Database error: " + ex.getMessage());
            }
        }, "y", "y", "y", "y", "y");
    }

    @Test
    void importMinimalBaselineFromYAML() {
        // Test creating a minimal baseline with only required fields
        String[] args = testCaseArgs("-f", MINIMAL_CONFIG_PATH,
                "-bvpE", TEST_TIFF_E_PATH,
                "-bvpP", TEST_TIFF_P_PATH);

        queueInteraction(() -> {
            new SymphonySetup(args);

            try {
                Integer bvId = getDbInterface().baselineVersionIdByName("minimal-baseline");
                assertNotNull(bvId, "Minimal baseline version should be created");
                this.bvId = bvId;

                BaselineVersion installedBaseline = getDbInterface().getBaselineVersion(bvId);
                assertNotNull(installedBaseline);
                assertEquals("minimal-baseline", installedBaseline.getName());
                assertEquals("A minimal baseline version for testing", installedBaseline.getDescription());
                assertEquals("2026-07-01", installedBaseline.getValidFrom().toString());

            } catch (SQLException ex) {
                fail("Database error: " + ex.getMessage());
            }
        }, "y");  // baseline only
    }

    @Test
    void importNationalAreasFromYAML() {
        // Test national areas import
        String[] args = testCaseArgs("-f", NATIONAL_AREAS_CONFIG_PATH);

        queueInteraction(() -> {
            new SymphonySetup(args);

            assertNationalAreasImportSuccess();
            getDbInterface().cleanNationalAreas();

        }, "y");
    }

    @Test
    void failOnMissingConfigFile() {
        // Test error handling for non-existent config file
        String[] args = testCaseArgs("-f", "src/test/resources/import/nonexistent.yaml");

        queueInteraction(() -> {
            new SymphonySetup(args);

            // Should produce an error message
            String errorOutput = displaceErr.toString();
            assertTrue(errorOutput.contains("Configuration file not found") ||
                            errorOutput.contains("Error reading configuration file"),
                    "Should report missing config file error");
        });
    }
}
