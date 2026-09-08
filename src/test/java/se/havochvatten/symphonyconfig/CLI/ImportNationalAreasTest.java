package se.havochvatten.symphonyconfig.CLI;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.database.DbTestInterface;
import se.havochvatten.symphonyconfig.setup.model.NationalArea;
import se.havochvatten.symphonyconfig.setup.process.NationalAreaRowInsert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImportNationalAreasTest extends CliTestBase {

    public final String[] args;

    ImportNationalAreasTest() {
        super(true);

        // cli arguments
        // -na  [national areas import] ( identifier(s) )            // repeatable argument
        // -naP [national areas, paths] ( local paths )              // repeatable argument like above, match positional
        // -naC [national areas country code(s)]                     // repeatable, single argument will be reused if
        //                                                              multiple identifiers are given.
        //                                                              Multiple args must match other
        this.args = testCaseArgs(
            "-na", "BOUNDARY,TEST",
            "-naP", String.format("%s,%s", nationalAreaBoundary, nationalAreaSelectable),
            "-naC", "SWE");
    }

    @Test
    void invokeNationalAreasImport() {

        queueInteraction(() -> {
            new SymphonySetup(args);

            try {
                assertNationalAreasImportSuccess();

            } catch (SQLException sqlx) {
                fail(sqlx.getMessage());
            } catch (IOException iox) {
                fail(iox.getMessage());
            }
        }, "y");
    }

    @Test
    void invocationAcceptedWithNonDefaultDbPortAndSchema() {
        // Regression test for SYM-712: 'checkNationalAreaInvocation' rejected every '-na'
        // invocation whenever '-dbPt'/'-dbS' were present, because the option set that
        // 'optionsExceptRequired()' filters against omitted them, so any leftover option
        // (including these two database connection options) was treated as an illegal
        // baseline-import option. CliTestBase's 'requiredArgs' already includes both options
        // whenever the test database is configured on a non-default port/schema (as it is
        // here), so this reproduces the defect with no extra setup.
        //
        // Declining the confirmation prompt keeps this test free of database fixtures:
        // if the invocation is (still) wrongly rejected, that happens before the prompt is
        // ever printed, and the rejection message lands on stderr instead.
        queueInteraction(() -> {
            new SymphonySetup(args);

            assertEquals("", displaceErr.toString().trim(),
                "A national areas import with '-dbPt'/'-dbS' present must not be rejected as an invalid invocation");
        }, "n");
    }

    @Test
    void reportDisallowedInvocation() { // baseline import and national areas
        String[] failingArgs = ArrayUtils.addAll(args, "-u", "-md", csvMetaFilePartialSV);

        queueInteraction(() -> {
            new SymphonySetup(failingArgs);

            assertEquals("Invalid invocation:\n" +
                "option(s) not valid for a national area import: md, u.",
                displaceErr.toString().trim());
        });
    }

    @Test
    void reportIncompleteInvocation() {
        String[] incompleteArgs = ArrayUtils.subarray(args, 0, args.length - 2);

        queueInteraction(() -> {
            new SymphonySetup(incompleteArgs);

            assertEquals("Invalid invocation:\n" +
                "some required national area import option was missing.\n " +
                "(-na, -naP, -naC are all required for the national area import procedure).", displaceErr.toString().trim());
        });
    }

    @Test
    void reportMismatchedInvocation() {
        String[] mismatchedArgs = testCaseArgs(
            "-na", "BOUNDARY,TEST,TEST2",
            "-naP", "some/path/1.json,some/path/2.json",
            "-naC", "SWE");

        queueInteraction(() -> {
            new SymphonySetup(mismatchedArgs);

            assertEquals("National area polygon path arguments must match number of specified area types.",
                displaceErr.toString().trim());
        });
    }

    @Test
    void importingOneCountryLeavesAnotherCountrysTypesRowIntact() throws Exception {
        // Import SWE through the normal path
        queueInteraction(() -> new SymphonySetup(
            testCaseArgs("-f", RESOURCES_PATH + "import/national-areas-import.yaml")), "y");

        List<NationalArea> sweBefore = getDbInterface().query(
            DbTestInterface.getAllNatAreasForCountryCodeQuery(dbSchema), NationalArea.handler, "SWE");
        assertTrue(sweBefore.stream().anyMatch(na -> "TYPES".equals(na.getType())),
            "Precondition: SWE has a TYPES row");

        // Now import a different country
        String kenConfig = writeKenyaConfig();
        queueInteraction(() -> new SymphonySetup(testCaseArgs("-f", kenConfig)), "y");

        List<NationalArea> sweAfter = getDbInterface().query(
            DbTestInterface.getAllNatAreasForCountryCodeQuery(dbSchema), NationalArea.handler, "SWE");

        assertTrue(sweAfter.stream().anyMatch(na -> "TYPES".equals(na.getType())),
            "Importing KEN must not delete SWE's TYPES row; AreasService uses getSingleResult() "
                + "and would then throw NATIONAL_AREA_NOT_FOUND for Sweden");
    }

    @Test
    void failedImportPartwayLeavesPreExistingRowsIntact() throws Exception {
        // Establish existing rows through the ordinary, successful path first, so there is
        // something for a subsequent failed import to (wrongly) destroy.
        queueInteraction(() -> new SymphonySetup(
            testCaseArgs("-f", RESOURCES_PATH + "import/national-areas-import.yaml")), "y");

        List<NationalArea> before = getDbInterface().query(
            DbTestInterface.getAllNatAreasForCountryCodeQuery(dbSchema), NationalArea.handler, "SWE");
        assertEquals(3, before.size(), "Precondition: BOUNDARY, TEST and TYPES rows present");

        // Exercises DbInterface.updateNationalAreas() directly, rather than through the CLI: the
        // '-na'/'-naP'/'-naC' switch invocation performs no file-existence check at all (that gap
        // is documented as out of scope), so driving this through the CLI would only prove the
        // pre-existing gap, not the fix under test. BOUNDARY's delete+insert succeeds; TEST's file
        // does not exist, so NationalAreaRowInsert.getPolygon() throws only after TEST's own row
        // has already been deleted within the same call. Before the transaction fix, that delete
        // would have been permanent under auto-commit.
        NationalAreaRowInsert[] partlyBadInserts = {
            new NationalAreaRowInsert("BOUNDARY", "SWE", nationalAreaBoundary),
            new NationalAreaRowInsert("TEST", "SWE", "/nonexistent/typo.json")
        };

        assertThrows(RuntimeException.class, () -> getDbInterface().updateNationalAreas(partlyBadInserts),
            "A missing polygon file must surface as a failure rather than silently succeeding");

        List<NationalArea> after = getDbInterface().query(
            DbTestInterface.getAllNatAreasForCountryCodeQuery(dbSchema), NationalArea.handler, "SWE");

        assertEquals(before.size(), after.size(),
            "A national areas import that fails part-way must not delete any pre-existing row");
        for (NationalArea row : before) {
            // The TYPES row has no 'areas' payload (narea_areas is null for it, unlike BOUNDARY/
            // TEST), so the comparison must tolerate null rather than calling equals() on it.
            assertTrue(after.stream().anyMatch(na ->
                    na.getType().equals(row.getType())
                        && java.util.Objects.equals(na.getAreasJson(), row.getAreasJson())),
                "Row of type " + row.getType() + " must survive unchanged after a failed partial import");
        }
    }

    /**
     * Writes a 'nationalAreas' config for KEN, reusing the existing SWE boundary/selectable
     * JSON fixtures (the countryISO comes from the config, not from the file contents). Must
     * satisfy the stricter validation rules: exactly one BOUNDARY entry, a single shared
     * countryISO across entries, every referenced file existing, and a 3-character countryISO.
     */
    private String writeKenyaConfig() {
        try {
            Path dir = Path.of("target/test-resources");
            Files.createDirectories(dir);
            Path p = dir.resolve("national-areas-ken.yaml");
            Files.writeString(p, String.join(NEW_LINE,
                "operation: nationalAreas",
                "nationalAreas:",
                "  - type: BOUNDARY",
                "    file: " + absoluteResourcePath("/import/national-area/test-national-boundary.json"),
                "    countryISO: KEN",
                "  - type: TEST",
                "    file: " + absoluteResourcePath("/import/national-area/test-national-selectable.json"),
                "    countryISO: KEN"));
            return p.toString();
        } catch (IOException e) {
            fail("Could not write config: " + e.getMessage());
            return null;
        }
    }

    @AfterEach
    void cleanNationalAreas() {
        getDbInterface().cleanNationalAreas();
    }
}
