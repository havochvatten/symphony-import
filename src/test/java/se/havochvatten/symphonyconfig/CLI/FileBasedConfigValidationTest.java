package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @AfterEach
    void removeInstalledBaseline() {
        Integer id = getDbInterface().getBaselineVersionByName("minimal-baseline");
        if (id != null) {
            getDbInterface().cleanBaselineVersion(id);
        }
    }
}
