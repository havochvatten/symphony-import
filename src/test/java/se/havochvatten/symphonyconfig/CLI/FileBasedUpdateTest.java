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
            deleteTempConfig(tempConfigPath);
        }
    }

    @Test
    void replaceIsConfirmedEvenWithoutOwnedMatricesOrPartitions() throws Exception {
        assertNotNull(bvId);

        // Seed metadata and a tool-imported matrix. sensm_owner stays NULL, which is the
        // ordinary state of an operator-managed baseline and precisely the case the old
        // owners-or-partitions gate let through unannounced.
        String[] seedArgs = testCaseArgs("-u",
            "-md", csvMetaFileCompleteEN, "-mdL", "en",
            "-mx", csvMatrixFileEN, "-mxN", csvMatrixCompleteName, "-mxL", "en",
            "-bv", String.valueOf(bvId));
        queueInteraction(() -> new SymphonySetup(seedArgs), "y", "y");

        int matrixId = getDbInterface().getMatrixMap(bvId).get(csvMatrixCompleteName);
        getDbInterface().installCalculationArea("TEST-Area-Collateral", matrixId, false);

        int valuesBefore = getDbInterface().countMetaValues(bvId);
        assertTrue(valuesBefore > 0, "Precondition: metadata present");

        String cfg = createTempReplaceConfig();
        try {
            // Decline at the replacement guard. One queued line: the guard prompt is the
            // first thing a replace asks, before any per-file import prompt.
            String config = cfg;
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", config)), "n");

            String out = displaceOut.toString();
            assertTrue(out.contains("Update mode 'replace' will permanently delete"),
                "A replace that destroys existing data must announce it, even when no matrix "
                    + "is user-owned and no reliability partition exists. stdout was: " + out);
            assertTrue(out.contains("sensitivity matrices: 1"),
                "The prompt must count the matrices it will delete. stdout was: " + out);
            assertTrue(out.contains("calculation areas: 1"),
                "The prompt must count the calculation areas it will delete. stdout was: " + out);
            assertTrue(out.contains("Replace procedure aborted interactively."));

            assertEquals(valuesBefore, getDbInterface().countMetaValues(bvId),
                "A declined replace must delete nothing");
            assertEquals(1, getDbInterface().countSensitivityMatrices(bvId),
                "A declined replace must leave the matrix in place");
        } finally {
            deleteTempConfig(cfg);
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
            }, "y", "y");   // guard prompt, then the single metadata file's prompt

        } finally {
            deleteTempConfig(tempConfigPath);
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
            }, "y", "y", "y");   // guard prompt, then one prompt per metadata file
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
    void requireConfirmationWhenReliabilityPolygonsArePresent() throws Exception {
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
            // Abort operation
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "n");

            String out = displaceOut.toString();
            assertTrue(
            out.contains("WARNING. The specified baseline version is coupled to 1 reliability partition"),
                "Replace operation must be confirmed explicitly by the user if certain "+
                "types of collateral data (in this case, reliability partition polygons) lingers. "+
                "+ stdout was: " + out);
            assertTrue(out.contains("Replace procedure aborted interactively."));

            assertEquals(valuesBefore, getDbInterface().countMetaValues(bvId),
                "A refused replace must not delete any metadata");
        } finally {
            getDbInterface().cleanReliabilityPartitions(bvId);
            deleteTempConfig(tempConfigPath);
        }
    }

    @Test
    void replaceOperationRemovesReliability() throws Exception {
        assertNotNull(bvId);

        // Seed metadata, then attach a reliability polygon as other tooling would
        String[] seedArgs = testCaseArgs("-u",
                "-md", csvMetaFileCompleteEN, "-mdL", "en", "-bv", String.valueOf(bvId));
        queueInteraction(() -> new SymphonySetup(seedArgs), "y");

        String tempConfigPath = null;
        try {
            // Installed inside the try-block to ensure panic measures in the
            // 'finally' clause are accessible following unexpected errors
            getDbInterface().installReliabilityPartition(bvId);
            int valuesBefore = getDbInterface().countMetaValues(bvId);
            assertTrue(valuesBefore == (8 * 3), "Precondition: metadata present");

            tempConfigPath = createTempReplaceConfig();
            String cfg = tempConfigPath;
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "y", "y");

            String out = displaceOut.toString();
            assertTrue(
                out.contains("WARNING. The specified baseline version is coupled to 1 reliability partition"));

            assertEquals(0, getDbInterface().countReliabilityPartitionRows(bvId),
                "Reliability polygons should be cleared when effecting a 'replace mode' import");
            assertEquals((7 * 3), getDbInterface().countMetaValues(bvId));
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

    @Test
    void replaceDeletesAreasCoupledToThisBaselineWhicheverVersionOwnsThem() throws Exception {
        assertNotNull(bvId);

        String[] seedArgs = testCaseArgs("-u",
            "-md", csvMetaFileCompleteEN, "-mdL", "en", "-bv", String.valueOf(bvId));
        queueInteraction(() -> new SymphonySetup(seedArgs), "y");

        int otherBvId = getDbInterface().installSecondaryBaselineVersion("TEST-Baseline-Other");
        int otherArea = -1;
        String cfg = null;

        try {
            // An area belonging to the OTHER baseline: its default matrix lives there
            int otherMatrix = getDbInterface()
                .provideDummySensitivityMatrixForCalcArea(otherBvId, "OTHER-Matrix");
            otherArea = getDbInterface()
                .installCalculationArea("OTHER-CalculationArea", otherMatrix, false);

            // ...carrying a secondary link to a matrix on the TARGET baseline. That link alone
            // puts the area in scope: replace is meant to leave nothing behind that referenced
            // the data it removes, whichever baseline version the area belongs to.
            int targetMatrix = getDbInterface()
                .provideDummySensitivityMatrixForCalcArea(bvId, "TARGET-Matrix");
            getDbInterface().linkCalculationAreaToMatrix(otherArea, targetMatrix);

            cfg = createTempReplaceConfig();
            String config = cfg;
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", config)), "y", "y");

            assertFalse(getDbInterface().calculationAreaExists(otherArea),
                "An area referencing a matrix on the targeted baseline version must be deleted "
                    + "even though another baseline version owns it. Narrowing the clear to the "
                    + "areas this version owns would leave the referencing area behind, which is "
                    + "the opposite of what replace is for");
            // Collapsed because layoutMessage wraps at 80 columns, and the clause lands across
            // the break for these counts.
            String unwrapped = displaceOut.toString().replaceAll("\\s+", " ");
            assertTrue(unwrapped.contains("1 of them belonging to another baseline version"),
                "The confirmation prompt must state that an area of another baseline version is "
                    + "among those it will delete. stdout was: " + displaceOut);
        } finally {
            if (otherArea > 0) {
                getDbInterface().deleteCalculationArea(otherArea);
            }
            getDbInterface().cleanBaselineVersion(otherBvId);
            deleteTempConfig(cfg);
        }
    }

    @Test
    void replaceSucceedsWhenAnOwnedAreaAlsoLinksToAnotherBaseline() throws Exception {
        assertNotNull(bvId);

        String[] seedArgs = testCaseArgs("-u",
            "-md", csvMetaFileCompleteEN, "-mdL", "en", "-bv", String.valueOf(bvId));
        queueInteraction(() -> new SymphonySetup(seedArgs), "y");

        int otherBvId = getDbInterface().installSecondaryBaselineVersion("TEST-Baseline-Other");
        String cfg = null;

        try {
            // An area owned by the TARGET baseline that also links to a matrix elsewhere.
            // casen_carea_fk has no ON DELETE action, so unless every link row for the area
            // goes, deleting the area fails and replace is impossible on this baseline.
            int targetMatrix = getDbInterface()
                .provideDummySensitivityMatrixForCalcArea(bvId, "TARGET-Matrix");
            int ownedArea = getDbInterface()
                .installCalculationArea("TARGET-CalculationArea", targetMatrix, false);

            int otherMatrix = getDbInterface()
                .provideDummySensitivityMatrixForCalcArea(otherBvId, "OTHER-Matrix");
            getDbInterface().linkCalculationAreaToMatrix(ownedArea, otherMatrix);

            cfg = createTempReplaceConfig();
            String config = cfg;
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", config)), "y", "y");

            assertFalse(displaceErr.toString().contains("casen_carea_fk"),
                "Replace must not fail on the link rows of an area it is deleting. stderr was: "
                    + displaceErr);
            assertEquals(0, getDbInterface().countCoupledCalculationAreas(bvId),
                "The area owned by this baseline must be gone after a replace");
            assertFalse(getDbInterface().calculationAreaExists(ownedArea),
                "The owned area must be deleted, not merely unlinked");
        } finally {
            getDbInterface().cleanBaselineVersion(otherBvId);
            deleteTempConfig(cfg);
        }
    }

    @Test
    void matrixReplaceDoesNotStrandScoresWhenACalculationAreaExists() throws Exception {
        assertNotNull(bvId);

        String[] seedArgs = testCaseArgs("-u",
            "-md", csvMetaFileCompleteEN, "-mdL", "en",
            "-mx", csvMatrixFileEN, "-mxN", csvMatrixCompleteName, "-mxL", "en",
            "-bv", String.valueOf(bvId));
        queueInteraction(() -> new SymphonySetup(seedArgs), "y", "y");

        int matrixId = getDbInterface().getMatrixMap(bvId).get(csvMatrixCompleteName);
        getDbInterface().installCalculationArea("TEST-Area-Blocking-Matrix", matrixId, false);

        int scoresBefore = getDbInterface().countSensitivityScores(bvId);
        assertTrue(scoresBefore > 0, "Precondition: the seeded matrix holds scores");

        // A matrix-only replace. carea_default_sensm_id has no ON DELETE action, so before the
        // fix the clear deleted and committed the sensitivity rows and then died on the matrix
        // delete, leaving a matrix with zero scores and a committed partial destruction.
        String[] replaceArgs = testCaseArgs("-u", "r",
            "-mx", csvMatrixFileEN, "-mxN", csvMatrixCompleteName, "-mxL", "en",
            "-bv", String.valueOf(bvId));
        queueInteraction(() -> new SymphonySetup(replaceArgs), "y", "y");

        String out = displaceOut.toString();
        assertTrue(out.contains("calculation areas: 1"),
            "The MATRICES-scope guard prompt must name the calculation area it will delete "
                + "along with the matrix it depends on; otherwise a regression that dropped the "
                + "guard here would go unnoticed since queued 'y' input is never verified as "
                + "consumed. stdout was: " + out);
        assertFalse(displaceErr.toString().contains("carea_default_sensm_fk"),
            "A matrix replace must clear the calculation areas that reference the matrices "
                + "before deleting them. stderr was: " + displaceErr);
        assertEquals(scoresBefore, getDbInterface().countSensitivityScores(bvId),
            "A matrix replace must end with a fully populated matrix, not an empty one");
        assertEquals(1, getDbInterface().countSensitivityMatrices(bvId),
            "The replaced matrix must exist exactly once");
    }

    @Test
    void failedCalculationAreaReplaceRollsBackTheClear() throws Exception {
        assertNotNull(bvId);

        // Seed an area under a matrix whose name does NOT match the one the gpkg fixture
        // references, so the import's matrix lookup throws after the clear has run.
        int matrixId = getDbInterface()
            .provideDummySensitivityMatrixForCalcArea(bvId, "UNRELATED-Matrix");
        getDbInterface().installCalculationArea("TEST-Area-Pre-Existing", matrixId, false);

        assertEquals(1, getDbInterface().countCoupledCalculationAreas(bvId),
            "Precondition: one calculation area present");

        String cfg = writeConfig("replace-calcareas.yaml",
            "operation: update",
            "baseline:",
            "  id: " + bvId,
            "  updateMode: replace",
            "calculationAreas:",
            "  file: " + absoluteResourcePath("/import/calcarea-package.gpkg"),
            "  allDefault: true");

        try {
            queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", cfg)), "y", "y");

            assertTrue(displaceErr.toString().contains("No sensitivity matrix named"),
                "Precondition: the import fails on the unresolvable matrix name. stderr was: "
                    + displaceErr);
            assertEquals(1, getDbInterface().countCoupledCalculationAreas(bvId),
                "A calculation area import that fails part way through must roll back its "
                    + "clear, not leave the baseline with neither the old areas nor the new");
        } finally {
            deleteTempConfig(cfg);
        }
    }

    @Test
    void calculationAreaOnlyReplaceGuardNamesWhatItWillDelete() throws Exception {
        assertNotNull(bvId);

        int matrixId = getDbInterface()
            .provideDummySensitivityMatrixForCalcArea(bvId, csvMatrixCompleteName);
        getDbInterface().installCalculationArea("TEST-Area-Guard-Only", matrixId, false);

        assertEquals(1, getDbInterface().countCoupledCalculationAreas(bvId),
            "Precondition: one calculation area present");

        // A CALCULATION_AREAS-scope replace, declined at the guard. Only one 'n' is queued:
        // withTextFromSystemIn tolerates unconsumed input, so if the guard stopped firing for
        // this scope, the import would run to completion unconfirmed rather than fail loudly.
        String[] replaceArgs = testCaseArgs("-u", "r",
            "-caF", calculationAreaPackage, "-caDA",
            "-bv", String.valueOf(bvId));
        queueInteraction(() -> new SymphonySetup(replaceArgs), "n");

        String out = displaceOut.toString();
        assertTrue(out.contains("Update mode 'replace' will permanently delete"),
            "A CALCULATION_AREAS-scope replace must show the guard prompt naming what it will "
                + "delete before it deletes anything. stdout was: " + out);
        assertTrue(out.contains("calculation areas: 1"),
            "The guard prompt must count the calculation area it will delete. stdout was: " + out);
        assertTrue(out.contains("Replace procedure aborted interactively."));

        assertEquals(1, getDbInterface().countCoupledCalculationAreas(bvId),
            "A declined replace must delete nothing");
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
