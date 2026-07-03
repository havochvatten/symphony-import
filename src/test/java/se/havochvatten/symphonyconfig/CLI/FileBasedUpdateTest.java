package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.model.Baseline;
import se.havochvatten.symphonyconfig.setup.model.SymphonyCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for updating an existing baseline using a JSON configuration file.
 */
public class FileBasedUpdateTest extends CliTestBase {
    private static final String UPDATE_CONFIG_TEMPLATE = "src/test/resources/import/baseline-update-metadata.json";
    private static final String TEMP_DIR = "target/test-resources";

    public FileBasedUpdateTest() {
        super(true);
    }

    @Test
    void updateExistingBaselineWithMetadataFromJSON() {
        assertNotNull(bvId);

        // Create a temporary config file with the current baseline ID
        String tempConfigPath = createTempConfigWithBaselineIdAndMetadata();

        try {
            String[] args = testCaseArgs("-f", tempConfigPath);

            queueInteraction(() -> {
                new SymphonySetup(args);

                try {
                    // Verify metadata was updated
                    Baseline bl = getDbInterface().getBaseline(bvId);
                    assertNotNull(bl);

                    // Verify metadata was added (should have bands after update)
                    int ecosystemBands = bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.size();
                    int pressureBands = bl.getComponents().get(SymphonyCategory.PRESSURE).bands.size();

                    assertEquals(4, ecosystemBands, "Should have 4 ecosystem bands after metadata import");
                    assertEquals(4, pressureBands, "Should have 4 pressure bands after metadata import");

                } catch (SQLException ex) {
                    fail("Database error: " + ex.getMessage());
                }
            }, "y", "y");

        } finally {
            // Clean up temp files
            try {
                Path tempDirPath = Path.of(TEMP_DIR);
                if (Files.exists(tempDirPath)) {
                    Files.walk(tempDirPath)
                        .sorted() // files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                System.err.println(String.format("Failed to delete file: %s%n%s", path, e.getMessage()));
                            }
                    });
                }
            } catch (IOException e) {
                System.err.println(
                    String.format("Warning: Failure to delete temp directory: %n%s", e.getMessage())
                );
            }
        }
    }

    /**
     * Create a temporary configuration file with the current baseline ID substituted.
     * Assumes bvId is not null
     *
     * @return Path to the temporary config file
     */
    private String createTempConfigWithBaselineIdAndMetadata() {
        try {
            // Ensure temp directory exists
            Path tempDirPath = Path.of(TEMP_DIR);
            Files.createDirectories(tempDirPath);

            String[] metadataFiles = {
                "metadata-wellformed-complete-sv.csv",
                "metadata-wellformed-complete-en.csv"
            };

            for (String metadataFile : metadataFiles) {
                Path sourcePath = Path.of(RESOURCES_PATH, "import/", metadataFile);
                Path destPath = tempDirPath.resolve(metadataFile);
                Files.copy(sourcePath, destPath, REPLACE_EXISTING);
            }

            // Read template and replace baseline ID
            String configContent = getResourceFileAndReplace(
                UPDATE_CONFIG_TEMPLATE,
                Map.of("\"id\": 1", "\"id\": " + bvId)
            );

            // Write to temp file
            Path tempFilePath = tempDirPath.resolve("baseline-update-metadata-" + bvId + ".json");
            Files.writeString(tempFilePath, configContent);

            return tempFilePath.toString();
        } catch (IOException e) {
            fail("Failed to create temporary config file: " + e.getMessage());
            return null;  // Unreachable
        }
    }
}
