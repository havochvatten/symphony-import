package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.model.Baseline;
import se.havochvatten.symphonyconfig.setup.model.BaselineVersion;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class InstallBaselineTest extends CliTestBase {
    private static final String TEST_BASELINE_NAME = "test-import-tiff";
    private static final String TEST_BASELINE_DESC = "Baseline version description";
    private static final String TEST_VALID_FROM_DATE = "2030-01-01";

    public InstallBaselineTest() {
        super(false);
    }

    @Test
    void invokeInstallOnlyNewBaselineVersionWithValidFrom() {

        // cli arguments
        // -n    [install new baseline version]
        // -bvN  [new baseline version name]             // mandatory, unique
        // -bvD  [new baseline version description]      // non-mandatory
        // -bvV  [new baseline version valid to ]        // non-mandatory ISO 8601 date
        // -bvpE [new baseline version Ecosystems GeoTIFF] // mandatory valid local path
        // -bvpE [new baseline version Pressures GeoTIFF] // mandatory valid local path
        String[] args = testCaseArgs("-n",
            "-bvN", TEST_BASELINE_NAME,
            "-bvD", TEST_BASELINE_DESC,
            "-bvV", TEST_VALID_FROM_DATE,
            "-bvpE", TEST_TIFF_E_PATH,
            "-bvpP", TEST_TIFF_P_PATH);

        queueInteraction(() -> {
            new SymphonySetup(args);

            try {
                assertTestBaselineVersion();

            } catch (SQLException ex) {
                fail(ex.getMessage());
            }
        }, "y");
    }

    @Test
    void failOnAmbiguosNewBaselineInvocation() {
        String[] failingArgs = testCaseArgs("-n",
                "-bvN", TEST_BASELINE_NAME,
                "-bvD", TEST_BASELINE_DESC,
                "-bvV", TEST_VALID_FROM_DATE,
                "-bvpE", TEST_TIFF_E_PATH,
                "-bvpP", TEST_TIFF_P_PATH,
                "-bv", "1");

        queueInteraction(() -> {
            new SymphonySetup(failingArgs);

            assertEquals(
                String.format("Error: ambiguos invocation.%n-n and -bv options cannot be issued at the same time."),
                displaceErr.toString().trim()
            );
        });
    }

    void assertTestBaselineVersion() throws SQLException {
        Integer bvId = getDbInterface().baselineVersionIdByName(TEST_BASELINE_NAME);
        assertNotNull(bvId);
        this.bvId = bvId;

        BaselineVersion installedBaselineVersion = getDbInterface().getBaselineVersion(bvId);
        assertNotNull(installedBaselineVersion);
        assertEquals(TEST_BASELINE_DESC, installedBaselineVersion.getDescription());
        assertEquals(TEST_VALID_FROM_DATE, installedBaselineVersion.getValidFrom().toString());
        assertEquals(TEST_TIFF_E_PATH, installedBaselineVersion.getEcoFilePath());
        assertEquals(TEST_TIFF_P_PATH, installedBaselineVersion.getPressureFilePath());
    }

    @Test
    void invokeInstallNewBaselineWithBilingualMetaAndMatrix() {
        queueInteraction(() -> {
            new SymphonySetup(installBilingualWithMatrixArgs());

            try {
                assertTestBaselineVersion();
                Baseline bl = getDbInterface().getBaseline(bvId); // relies on bvId getting set as side effect
                assertBilingualMatrixBaseline(bl);

            } catch (SQLException ex) {
                fail(ex.getMessage());
            }
        }, "y", "y", "y", "y", "y");
    }
}
