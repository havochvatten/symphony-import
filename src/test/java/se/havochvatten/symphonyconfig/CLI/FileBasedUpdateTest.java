package se.havochvatten.symphonyconfig.CLI;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.config.ImportConfigFile;
import se.havochvatten.symphonyconfig.setup.model.Baseline;
import se.havochvatten.symphonyconfig.setup.model.SymphonyCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for updating an existing baseline using a JSON configuration file.
 */
public class FileBasedUpdateTest extends CliTestBase {
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

    @Test
    void replaceExistingMetadataFromJSON() {
        assertNotNull(bvId);

        // Seed the baseline with complete metadata to establish a known initial state
        // -u   [update]
        // -md  [metadata import] ( path )
        // -mdL [metadata language]
        // -bv  [baseline version database id]
        String[] seedArgs = testCaseArgs("-u",
            "-md", csvMetaFileCompleteEN,
            "-mdL", "en",
            "-bv", String.valueOf(bvId));

        // queue input to commence seeding ('y')
        queueInteraction(() -> new SymphonySetup(seedArgs), "y");

        // Confirm seed state: 4 ecosystem and 4 pressure bands
        try {
            Baseline seeded = getDbInterface().getBaseline(bvId);
            assertEquals(4, seeded.getComponents().get(SymphonyCategory.PRESSURE).bands.size(),
                "Seed step should produce 4 pressure bands");
        } catch (Exception ex) {
            fail("Database error during seed verification: " + ex.getMessage());
        }

        // Create a replace config with partial sv metadata
        String tempConfigPath = createTempReplaceConfig();

        try {
            String[] args = testCaseArgs("-f", tempConfigPath);

            queueInteraction(() -> {
                new SymphonySetup(args);

                try {
                    Baseline bl = getDbInterface().getBaseline(bvId);
                    assertNotNull(bl);

                    // Replace mode cleared the existing complete data before re-importing partial sv:
                    // the 4th pressure band from the seed import should no longer exist
                    assertEquals(4, bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.size());
                    assertEquals(3, bl.getComponents().get(SymphonyCategory.PRESSURE).bands.size(),
                        "Replace mode should have cleared seeded data; 4th pressure band should be absent");

                    assertTrue(bl.isMetaIncomplete(),
                        "Baseline should be incomplete after replacing complete data with partial");

                } catch (SQLException ex) {
                    fail("Database error: " + ex.getMessage());
                }
            }, "y");

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


    private record MetadataFileSpec(String fileName, String language) {}
    
    /**
     * Create a temporary configuration file for a replace operation with the current baseline ID substituted.
     * Assumes bvId is not null.
     *
     * @return Path to the temporary config file
     */
    private String createTempReplaceConfig() {
        return createTempUpdateConfig("replace",
            new MetadataFileSpec("metadata-wellformed-partial-sv.csv", "sv"));
    }

    /**
     * Create a temporary configuration file with the current baseline ID substituted.
     * Assumes bvId is not null
     *
     * @return Path to the temporary config file
     */
    private String createTempConfigWithBaselineIdAndMetadata() {
        return createTempUpdateConfig("update",
            new MetadataFileSpec("metadata-wellformed-complete-sv.csv", "sv"),
            new MetadataFileSpec("metadata-wellformed-complete-en.csv", "en"));
    }

    /**
     * Utility to create a temporary update configuration file.
     * Copies specified metadata files to temp directory and generates a config pointing to them.
     *
     * @param updateMode "update" or "replace"
     * @param metadataFiles metadata file specifications (file name and language)
     * @return Path to the generated config file
     */
    private String createTempUpdateConfig(String updateMode, MetadataFileSpec... metadataFiles) {
        try {
            Path tempDirPath = Path.of(TEMP_DIR);
            Files.createDirectories(tempDirPath);

            // Copy metadata files to temp directory
            for (MetadataFileSpec spec : metadataFiles) {
                Path sourcePath = Path.of(RESOURCES_PATH, "import", spec.fileName);
                Path destPath = tempDirPath.resolve(spec.fileName);
                Files.copy(sourcePath, destPath, REPLACE_EXISTING);
            }

            // Build config programmatically
            ImportConfigFile config = new ImportConfigFile();
            config.setOperation("update");

            ImportConfigFile.BaselineConfig baselineConfig = new ImportConfigFile.BaselineConfig();
            baselineConfig.setId(bvId);
            baselineConfig.setUpdateMode(updateMode);
            config.setBaseline(baselineConfig);

            List<ImportConfigFile.MetadataConfig> metadataConfigs = new ArrayList<>();
            for (MetadataFileSpec spec : metadataFiles) {
                ImportConfigFile.MetadataConfig mdConfig = new ImportConfigFile.MetadataConfig();
                mdConfig.setFile(spec.fileName);
                mdConfig.setLanguage(spec.language);
                metadataConfigs.add(mdConfig);
            }
            config.setMetadata(metadataConfigs);

            // Serialize to JSON
            ObjectMapper mapper = new ObjectMapper();
            Path configFilePath = tempDirPath.resolve("baseline-" + updateMode + "-metadata-" + bvId + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFilePath.toFile(), config);

            return configFilePath.toString();
        } catch (IOException e) {
            fail("Failed to create temporary config file: " + e.getMessage());
            return null;  // Unreachable
        }
    }
}
