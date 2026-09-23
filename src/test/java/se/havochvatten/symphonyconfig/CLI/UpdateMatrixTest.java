package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.model.Baseline;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class UpdateMatrixTest extends CliTestBase {

    public UpdateMatrixTest() {
        super(true);
    }

    @Test
    void invokeCompleteImportWithMatrixOption() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path to import )
        // -mdL [metadata language] ( ISO-639 language code )
        // -bv  [baseline version database id]

        String[] args = testCaseArgs("-u",
            "-md", csvMetaFileCompleteSV,
            "-mdL", "sv",
            "-bv", String.valueOf(bvId),
            "-mx", csvMatrixFileSV,
            "-mxN", csvMatrixCompleteName,
            "-mxL", "sv");

        // queue input to commence import procedure ('y')
        queueInteraction(() -> {
            new SymphonySetup(args);

            try {
                System.out.println(dbInterface.sensitivityControlQuery("sv", "Kustfågel", "Syrebristbakgrund", csvMatrixCompleteName));

                Double expect0_4 = dbInterface.readSensitivityValue("sv",
                    "Kustfågel", "Syrebristbakgrund", csvMatrixCompleteName),
                       expect1_0 = dbInterface.readSensitivityValue("sv",
                        "Torsk", "Fångst bottentrål", csvMatrixCompleteName);

                assertEquals(0.4, expect0_4);
                assertEquals(1.0, expect1_0);

            } catch (SQLException sqlx) {
                System.err.println(sqlx.getMessage());
                fail("SQL error occurred: " + sqlx.getMessage());
            }

        }, "y", "y");
    }

    @Test
    void invokeBilingualWithENMatrix() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path )          // repeated argument
        // -mdL [metadata language] ( language )    // repeated arg (two languages)
        // -mx  [sensitivity matrix import] ( path )
        // -mxN [sensitivity matrix name/title]     // mandatory when mx import given
        // -mxL [sensitivity matrix language]
        // -bv  [baseline version database id]

        String[] args = testCaseArgs("-u",
            "-md", csvMetaFileCompleteSV,
                   csvMetaFileCompleteEN,
            "-mdL", "sv", "en",
            "-mx", csvMatrixFileEN,
            "-mxN", csvMatrixCompleteName,
            "-mxL", "en",
            "-bv", String.valueOf(bvId));

        // queue input to commence import procedure (2x 'y' to confirm both files)
        queueInteraction(() -> {
            new SymphonySetup(args);

            try {
                Baseline bl = getDbInterface().getBaseline(bvId);

                try {
                    assertBilingualMatrixBaseline(bl);
                } catch (SQLException sqlx) {
                    System.err.println(sqlx.getMessage());
                    fail("SQL error occurred: " + sqlx.getMessage());
                }

            } catch (Exception e) {
                fail();
            }
        }, "y", "y", "y");
    }

    @Test
    void aVeryLongMatrixNameStillReachesThePrompt() {
        // MatrixBase.getImportItemName() returns the operator-supplied name untruncated. The
        // name below is a single 88 character token with no spaces at all, so it cannot be
        // wrapped at a word boundary: the only way the tail survives is a hard break through the
        // token itself. A name built from ordinary words, however long, would still wrap cleanly
        // under the old regex and would not exercise the defect.
        String longName = "Sensitivity_matrix_with_an_unusually_long_operator_supplied_name_that_has_no_spaces_TEST";

        String[] args = testCaseArgs("-u",
            "-md", csvMetaFileCompleteSV,
            "-mdL", "sv",
            "-bv", String.valueOf(bvId),
            "-mx", csvMatrixFileSV,
            "-mxN", longName,
            "-mxL", "sv");

        queueInteraction(() -> {
            new SymphonySetup(args);

            String out = displaceOut.toString();
            assertTrue(out.contains("Pending matrix import:"),
                "stdout was: " + out);
            assertTrue(out.replace(NEW_LINE, "").replace("\n", "").contains(longName),
                "The full over-long token must survive the prompt layout, reassembled across "
                    + "whatever hard break split it. stdout was: " + out);
        }, "y", "y");
    }

    @Test
    void failOnMatrixImportWithPartialMetadata() {
        String[] args = testCaseArgs("-u",
            "-md", csvMetaFilePartialSV,
            "-mdL", "sv",
            "-mx",  csvMatrixFileSV,
            "-mxN", csvMatrixCompleteName,
            "-mxL", "sv",
            "-bv", String.valueOf(bvId));

        // queue input to commence import procedure (2x 'y' to confirm both files)
        queueInteraction(() -> {
            new SymphonySetup(args);

                // Expected error message
                assertEquals(
                    "Matrix import is not possible for baseline version with incomplete meta band coverage",
                    displaceErr.toString().trim()
                );
            }, "y");
    }
}
