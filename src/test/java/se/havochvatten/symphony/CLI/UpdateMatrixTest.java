package se.havochvatten.symphony.CLI;

import org.junit.jupiter.api.Test;
import se.havochvatten.symphony_setup.setup.SymphonySetup;
import se.havochvatten.symphony_setup.setup.model.Baseline;
import se.havochvatten.symphony_setup.setup.model.SymphonyBand;
import se.havochvatten.symphony_setup.setup.model.SymphonyCategory;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class UpdateMatrixTest extends CliTestBase {

    @Test
    void invokeCompleteImportWithMatrixOption() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path to import )            // repeatable argument
        // -mdL [metadata language] ( ISO-639 language code )   // repeatable argument like above
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

                    for (SymphonyBand ecoBand : bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.values()) {
                        for (SymphonyBand prBand : bl.getComponents().get(SymphonyCategory.PRESSURE).bands.values()) {
                            assertEquals(
                                dbInterface.readSensitivityValue("sv", ecoBand.getTitle("sv"), prBand.getTitle("sv"), csvMatrixCompleteName),
                                dbInterface.readSensitivityValue("en", ecoBand.getTitle("en"), prBand.getTitle("en"), csvMatrixCompleteName)
                            );
                        }
                    }
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
