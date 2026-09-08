package se.havochvatten.symphonyconfig.CLI;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.config.ImportConfigFile;
import se.havochvatten.symphonyconfig.setup.model.Baseline;
import se.havochvatten.symphonyconfig.setup.model.SymphonyBand;
import se.havochvatten.symphonyconfig.setup.model.SymphonyCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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


    @Test
    void replaceWithTwoMetadataFilesKeepsBothLanguages() {
        assertNotNull(bvId);

        // Seed both languages through the CLI path
        String[] seedArgs = testCaseArgs("-u",
            "-md", csvMetaFileCompleteEN,
            "-md", csvMetaFileCompleteSV,
            "-mdL", "en", "sv",
            "-bv", String.valueOf(bvId));
        queueInteraction(() -> new SymphonySetup(seedArgs), "y", "y");

        // A replace config listing BOTH metadata files, exactly as IMPORT-CONFIG.md sanctions
        String tempConfigPath = createTempReplaceConfigWithBothLanguages();

        try {
            String[] args = testCaseArgs("-f", tempConfigPath);

            queueInteraction(() -> {
                new SymphonySetup(args);

                try {
                    Baseline bl = getDbInterface().getBaseline(bvId);
                    assertNotNull(bl);

                    assertEquals(4, bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.size());
                    assertEquals(4, bl.getComponents().get(SymphonyCategory.PRESSURE).bands.size());

                    // Both languages must survive. Before the fix, the first file's
                    // language is wiped by the second file's clearBandData call.
                    for (SymphonyBand band : bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.values()) {
                        assertNotNull(band.getTitle("en"),
                            "English metadata must survive a two-file replace");
                        assertNotNull(band.getTitle("sv"),
                            "Swedish metadata must survive a two-file replace; "
                                + "clearBandData must run once per update, not once per file");
                    }
                } catch (SQLException ex) {
                    fail("Database error: " + ex.getMessage());
                }
            }, "y", "y");
        } finally {
            deleteTempConfig(tempConfigPath);
        }
    }

    @Test
    void badLanguageOnSecondMetadataEntryIsRejectedBeforeAnythingIsWritten() throws Exception {
        assertNotNull(bvId);

        assertEquals(0, getDbInterface().countMetaValues(bvId), "Precondition: no metadata yet");

        // First entry is well-formed; only the SECOND entry's language is bad. Before the fix,
        // validation of mdSettings.validate() (which checks language) ran inside the import loop,
        // so the first file's clear-and-import would already have committed by the time the
        // second entry's bad language was discovered.
        String cfg = writeConfig("metadata-bad-second-language.yaml",
            "operation: update",
            "baseline:",
            "  id: " + bvId,
            "metadata:",
            "  - file: " + absoluteResourcePath("/import/metadata-wellformed-complete-en.csv"),
            "    language: en",
            "  - file: " + absoluteResourcePath("/import/metadata-wellformed-complete-sv.csv"),
            "    language: english");

        try {
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "y", "y");

            assertTrue(displaceErr.toString().contains("metadata[1].language"),
                "Expected rejection mentioning 'metadata[1].language'. stderr was: " + displaceErr);

            assertEquals(0, getDbInterface().countMetaValues(bvId),
                "A config-wide validation failure must leave zero metadata rows written, not the "
                    + "first entry's data committed before the second entry's bad language is caught");
        } finally {
            deleteTempConfig(cfg);
        }
    }

    @Test
    void replaceIsRefusedWhenReliabilityPolygonsWouldBlockIt() throws Exception {
        assertNotNull(bvId);

        // Seed metadata, then attach a reliability polygon as other tooling would
        String[] seedArgs = testCaseArgs("-u",
            "-md", csvMetaFileCompleteEN, "-mdL", "en", "-bv", String.valueOf(bvId));
        queueInteraction(() -> new SymphonySetup(seedArgs), "y");

        String tempConfigPath = null;
        try {
            // Installed inside the try: everything from here on must be reachable by the
            // finally, or a stray failure leaves the reliability row in place and the
            // @AfterEach cleanBaselineVersion then trips the same RESTRICT foreign key
            getDbInterface().installReliabilityPartition(bvId);
            int valuesBefore = getDbInterface().countMetaValues(bvId);
            assertTrue(valuesBefore > 0, "Precondition: metadata present");

            tempConfigPath = createTempReplaceConfig();
            String cfg = tempConfigPath;
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "y");

            String err = displaceErr.toString();
            assertTrue(err.contains("reliability"),
                "Replace must be refused with an explanatory message. stderr was: " + err);
            assertTrue(err.contains("Cannot run updateMode 'replace'"),
                "The refusal must be the tool's own pre-flight message, not a raw foreign key "
                    + "error raised half-way through the delete. stderr was: " + err);

            assertEquals(valuesBefore, getDbInterface().countMetaValues(bvId),
                "A refused replace must not delete any metadata");
        } finally {
            getDbInterface().cleanReliabilityPartitions(bvId);
            deleteTempConfig(tempConfigPath);
        }
    }

    @Test
    void replaceNamesUserOwnedMatricesItWillEmpty() throws Exception {
        assertNotNull(bvId);

        String[] seedArgs = testCaseArgs("-u",
            "-md", csvMetaFileCompleteEN, "-mdL", "en",
            "-mx", csvMatrixFileEN, "-mxN", csvMatrixCompleteName, "-mxL", "en",
            "-bv", String.valueOf(bvId));
        queueInteraction(() -> new SymphonySetup(seedArgs), "y", "y");

        getDbInterface().setMatrixOwner(csvMatrixCompleteName, "alice@example.org");

        String tempConfigPath = null;
        try {
            tempConfigPath = createTempReplaceConfig();
            String cfg = tempConfigPath;
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "n");

            String out = displaceOut.toString();
            assertTrue(out.contains("alice@example.org"),
                "The confirmation prompt must name the owners whose matrix data will be emptied. "
                    + "stdout was: " + out);
        } finally {
            deleteTempConfig(tempConfigPath);
        }
    }

    @Test
    void replacePromptSaysItWillDeleteExistingData() {
        assertNotNull(bvId);

        // Seed the baseline so the replace has something to delete
        String[] seedArgs = testCaseArgs("-u",
            "-md", csvMetaFileCompleteEN, "-mdL", "en", "-bv", String.valueOf(bvId));
        queueInteraction(() -> new SymphonySetup(seedArgs), "y");

        String tempConfigPath = null;
        try {
            tempConfigPath = createTempReplaceConfig();
            String cfg = tempConfigPath;

            // Answer 'n': we only want to read the prompt, not carry out the replace
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "n");

            String out = displaceOut.toString();
            assertTrue(out.contains("REPLACE MODE"),
                "A replace-mode prompt must say that existing data will be deleted, so it cannot "
                    + "be mistaken for an ordinary update. The prompt's own wording is asserted "
                    + "rather than any mention of deletion: the pre-flight warning already says "
                    + "'deletes' whenever a user-owned matrix exists, which would let this pass "
                    + "with the prompt unchanged. stdout was: " + out);
        } finally {
            deleteTempConfig(tempConfigPath);
        }
    }

    @Test
    void ordinaryUpdatePromptCarriesNoReplaceNotice() {
        assertNotNull(bvId);

        String tempConfigPath = null;
        try {
            // Same config shape, updateMode 'update' instead of 'replace'
            tempConfigPath = createTempConfigWithBaselineIdAndMetadata();
            String cfg = tempConfigPath;

            // Answer 'n': nothing is written, we only want to read the prompt
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "n");

            String out = displaceOut.toString();
            assertFalse(out.contains("REPLACE MODE"),
                "A non-destructive 'update' must not carry the replace notice, or the notice "
                    + "would tell the operator nothing. stdout was: " + out);
        } finally {
            deleteTempConfig(tempConfigPath);
        }
    }

    @Test
    void namedDefaultAreasAreApplied() throws Exception {
        assertNotNull(bvId);

        // 'defaultAreas' is the documented alternative to 'allDefault' and was untested.
        // This belongs here rather than in FileBasedConfigValidationTest: that class is
        // super(false) and has no bvId to attach a calculation-area update to.
        //
        // The calcarea-package.gpkg fixture's two areas ('test-calc-area-1',
        // 'test-calc-area-2') both reference the matrix named csvMatrixCompleteName, so a
        // matrix of that name must exist on this baseline before the import runs, exactly as
        // ImportCalculationAreasTest#testImportDefaultCalculationAreas seeds it.
        getDbInterface().provideDummySensitivityMatrixForCalcArea(bvId, csvMatrixCompleteName);

        String cfg = writeConfig("named-defaults.yaml",
            "operation: update",
            "baseline:",
            "  id: " + bvId,
            "calculationAreas:",
            "  file: " + absoluteResourcePath("/import/calcarea-package.gpkg"),
            "  defaultAreas:",
            "    - test-calc-area-1");

        queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "y");

        // exactly one area flagged default...
        assertEquals(1, getDbInterface().countDefaultCalculationAreas(bvId),
            "Only one area should be marked default");

        // ...and it must be the NAMED one, not merely some one area. A count-only assertion
        // would still pass if CalcAreaProcedure flagged the wrong area (e.g. a wrong-name or
        // off-by-one bug that defaulted 'test-calc-area-2' instead), so identity is asserted
        // in both directions.
        assertTrue(getDbInterface().isCalculationAreaDefault(bvId, "test-calc-area-1"),
            "The named area 'test-calc-area-1' must be marked default");
        assertFalse(getDbInterface().isCalculationAreaDefault(bvId, "test-calc-area-2"),
            "The un-named area 'test-calc-area-2' must not be marked default");
    }

    @Test
    void csvSettingsOverrideTheDelimiter() throws Exception {
        assertNotNull(bvId);

        // This belongs here rather than in FileBasedConfigValidationTest for the same bvId
        // reason as above.
        //
        // A naive ';' -> ',' replace over metadata-wellformed-complete-en.csv would produce a
        // broken CSV: that file's 'summary' column contains commas inside field values (e.g.
        // "...bridges, lighthouses at sea, wind power turbines..."), so converting ';' to ','
        // would turn those into extra columns. Pipe ('|') does not occur anywhere in the
        // source fixture, so it cannot collide with the content; verified below, and
        // independently confirmed with a standalone parse before this test was written.
        Path pipeFile = Path.of(TEMP_DIR, "metadata-pipe-en.csv");
        Files.createDirectories(pipeFile.getParent());
        String pipeContent = Files.readString(Path.of(csvMetaFileCompleteEN)).replace(';', '|');
        Files.writeString(pipeFile, pipeContent);

        // Verify the generated fixture actually parses as 6 well-formed columns before using
        // it to drive the CLI, so a parsing failure here is not mistaken for the behaviour
        // under test. (The BOM prefix on line 1 does not affect the '|' count.)
        List<String> lines = Files.readAllLines(pipeFile);
        long expectedColumns = lines.get(0).chars().filter(c -> c == '|').count() + 1;
        assertEquals(6, expectedColumns, "Precondition: the source fixture has 6 columns");
        for (String line : lines) {
            if (line.isBlank()) continue;
            long columns = line.chars().filter(c -> c == '|').count() + 1;
            assertEquals(expectedColumns, columns,
                "Generated pipe-delimited fixture must be well-formed: " + line);
        }

        String cfg = writeConfig("csv-settings.yaml",
            "operation: update",
            "baseline:",
            "  id: " + bvId,
            "metadata:",
            "  - file: " + pipeFile.toAbsolutePath(),
            "    language: en",
            "csvSettings:",
            "  delimiter: \"|\"");

        queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "y");

        assertTrue(getDbInterface().countMetaValues(bvId) > 0,
            "A pipe-delimited file must import when csvSettings.delimiter is '|'");
    }

    private String writeConfig(String name, String... lines) {
        try {
            Path dir = Path.of(TEMP_DIR);
            Files.createDirectories(dir);
            Path p = dir.resolve(name);
            Files.writeString(p, String.join(NEW_LINE, lines));
            return p.toString();
        } catch (IOException e) {
            fail("Could not write config: " + e.getMessage());
            return null;
        }
    }

    private String createTempReplaceConfigWithBothLanguages() {
        try {
            Path tempDir = Path.of(TEMP_DIR);
            Files.createDirectories(tempDir);

            // The config references the metadata files by name, so copy them next to it
            Files.copy(Path.of(csvMetaFileCompleteEN),
                tempDir.resolve("metadata-wellformed-complete-en.csv"), REPLACE_EXISTING);
            Files.copy(Path.of(csvMetaFileCompleteSV),
                tempDir.resolve("metadata-wellformed-complete-sv.csv"), REPLACE_EXISTING);

            String yaml = String.join(NEW_LINE,
                "operation: update",
                "baseline:",
                "  id: " + bvId,
                "  updateMode: replace",
                "metadata:",
                "  - file: metadata-wellformed-complete-en.csv",
                "    language: en",
                "  - file: metadata-wellformed-complete-sv.csv",
                "    language: sv");

            Path configPath = tempDir.resolve("replace-two-languages.yaml");
            Files.writeString(configPath, yaml);
            return configPath.toString();
        } catch (IOException e) {
            fail("Could not create temporary config: " + e.getMessage());
            return null;
        }
    }

    private void deleteTempConfig(String path) {
        if (path != null) {
            try {
                Files.deleteIfExists(Path.of(path));
            } catch (IOException ignored) {
                // temp cleanup only
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
        return createTempUpdateConfig(SymphonySetup.UpdateMode.REPLACE,
            new MetadataFileSpec("metadata-wellformed-partial-sv.csv", "sv"));
    }

    /**
     * Create a temporary configuration file with the current baseline ID substituted.
     * Assumes bvId is not null
     *
     * @return Path to the temporary config file
     */
    private String createTempConfigWithBaselineIdAndMetadata() {
        return createTempUpdateConfig(SymphonySetup.UpdateMode.UPDATE,
            new MetadataFileSpec("metadata-wellformed-complete-sv.csv", "sv"),
            new MetadataFileSpec("metadata-wellformed-complete-en.csv", "en"));
    }

    /**
     * Utility to create a temporary update configuration file.
     * Copies specified metadata files to temp directory and generates a config pointing to them.
     *
     * @param updateMode UpdateMode.UPDATE or UpdateMode.REPLACE
     * @param metadataFiles metadata file specifications (file name and language)
     * @return Path to the generated config file
     */
    private String createTempUpdateConfig(SymphonySetup.UpdateMode updateMode, MetadataFileSpec... metadataFiles) {
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
            config.setOperation(ImportConfigFile.Operation.UPDATE);

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
