package se.havochvatten.symphonyconfig.CLI;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.model.NationalArea;

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
    void reportDisallowedInvocation() { // baseline import and national areas
        String[] failingArgs = ArrayUtils.addAll(args, "-u", "-md", csvMetaFilePartialSV);

        queueInteraction(() -> {
            new SymphonySetup(failingArgs);

            assertEquals("Invalid invocation:\n" +
                "both baseline and national area import options were provided.",
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

    @AfterEach
    void cleanNationalAreas() {
        getDbInterface().cleanNationalAreas();
    }
}
