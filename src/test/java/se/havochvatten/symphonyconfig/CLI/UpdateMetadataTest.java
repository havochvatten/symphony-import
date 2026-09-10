package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.model.Baseline;
import se.havochvatten.symphonyconfig.setup.model.SymphonyCategory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.*;


class UpdateMetadataTest extends CliTestBase {

    private static final String LONG_FILENAME_CSV =           "a_long_truncated_file_name_given_as_a_parameter_ABCDEFGHILJKLMNOPQRSTUVWXYX0123.csv";
    private static final String LONG_TRUNCATED_FILENAME_CSV = "a_long_truncated_file_name_given_as_a_parameter_ABCDEFGHILJKLMNOPQRSTU...csv";

    public UpdateMetadataTest() {
        super(true);
    }

    @Test
    void invokeWithoutBaselineVersionAndAbort() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path to import )            // repeatable argument
        // -mdL [metadata language] ( ISO-639 language code )   // repeatable argument like above
        // -bv  [baseline version database id] (omitted in this case ,
        //                                      should trigger the interaction tested below)

        String[] args = testCaseArgs("-u", "-md", csvMetaFilePartialSV, "-mdL", "sv");

        // queue input to abort: ('a')
        queueInteraction(() -> {
            new SymphonySetup(args);

            String output = displaceOut.toString();

            assertTrue(output.startsWith(
                    "Tool invoked without specifying baseline version id."
            ));
            assertTrue(output.endsWith(
                    "Task aborted interactively." + NEW_LINE
            ));
        }, "a");
    }

    @Test
    void invokeWithBaselineVersionAndAbort() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path to import )            // repeatable argument
        // -mdL [metadata language] ( ISO-639 language code )   // repeatable argument like above
        // -bv  [baseline version database id]

        String[] args = testCaseArgs( "-u", "-md", csvMetaFilePartialSV, "-mdL", "sv", "-bv", String.valueOf(bvId));

        // queue input to abort import: (any key but 'y')
        queueInteraction(() -> {

            new SymphonySetup(args);

            assertTrue(displaceOut.toString().startsWith(
                "Pending metadata import:")
            );

            try {
                Baseline bl = getDbInterface().getBaseline(bvId);

                // assert import was aborted: no band information was added (expect 0)
                assertEquals(0, bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.size());
                assertEquals(0, bl.getComponents().get(SymphonyCategory.PRESSURE).bands.size());

            } catch (Exception e) {
                fail();
            }
        }, "a");
    }

    @Test
    void invokeWithBaselineVersionAndImportPartialCSV() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path to import )            // repeatable argument
        // -mdL [metadata language] ( ISO-639 language code )   // repeatable argument like above
        // -bv  [baseline version database id]

        String[] args = testCaseArgs("-u", "-md", csvMetaFilePartialSV, "-mdL", "sv", "-bv", String.valueOf(bvId));

        // queue input to commence import procedure ('y')
        queueInteraction(() -> {
            new SymphonySetup(args);

            assertTrue(displaceOut.toString().startsWith(
                "Pending metadata import:")
            );

            try {
                Baseline bl = getDbInterface().getBaseline(bvId);

                assertEquals(4, bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.size());
                assertEquals(3, bl.getComponents().get(SymphonyCategory.PRESSURE).bands.size());

                assertTrue(bl.isMetaIncomplete());

            } catch (Exception e) {
                fail();
            }
        }, "y");
    }

    @Test
    void invokeWithBaselineVersionAndImportPartialExcel() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import]   ( path )
        // -mdL [metadata language] ( language )
        // -bv  [baseline version database id]

        String[] args = testCaseArgs("-u", "-md", excelMetaFilePartial, "-mdL", "sv", "-bv", String.valueOf(bvId));

        // queue input to commence import procedure ('y')
        queueInteraction(() -> {
            new SymphonySetup(args);

            assertTrue(displaceOut.toString().startsWith(
                "Pending metadata import:")
            );

            try {
                Baseline bl = getDbInterface().getBaseline(bvId);

                //
                assertEquals(4, bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.size());
                assertEquals(3, bl.getComponents().get(SymphonyCategory.PRESSURE).bands.size());

                assertTrue(bl.isMetaIncomplete());

            } catch (Exception e) {
                fail();
            }
        }, "y");
    }

    @Test
    void invokeWithMultipleImportFormats() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path ) -md ( path )  // repeatable argument
        // -mdL [metadata language] ( language )         // single argument for multiple files: will fall back to last
        // -bv  [baseline version database id]

        String[] args = testCaseArgs("-u",
            "-md", xlsxMetaFilePartialEcoSV,
            "-md", csvMetaFilePartialPressureSV,
            "-mdL", "sv", "-bv", String.valueOf(bvId));

        // queue input to commence import procedure (2x 'y' to confirm both files)
        queueInteraction(() -> {
            new SymphonySetup(args);

            assertEquals(displaceOut.toString().substring(0, 70),
                "Notice:\n" +
                    "Language parameter missing for the 2nd provided metadata file.");

            try {
                Baseline bl = getDbInterface().getBaseline(bvId);

                assertEquals(4, bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.size());
                assertEquals(4, bl.getComponents().get(SymphonyCategory.PRESSURE).bands.size());

                assertFalse(bl.isMetaIncomplete());

            } catch (Exception e) {
                fail();
            }
        }, "y", "y");
    }

    @Test
    void invokeWithMultipleLanguages() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path ) -md ( path )  // repeatable argument
        // -mdL [metadata language] ( language )         // single argument for multiple files: will fall back to last
        // -bv  [baseline version database id]

        String[] args = testCaseArgs("-u",
            "-md", csvMetaFileCompleteSV,
            "-md", csvMetaFileCompleteEN,
            "-mdL", "sv", "en",
            "-bv", String.valueOf(bvId));

        // queue input to commence import procedure (2x 'y' to confirm both files)
        queueInteraction(() -> {
            new SymphonySetup(args);

            try {
                Baseline bl = getDbInterface().getBaseline(bvId);

                assertEquals("Artificial reef",
                    bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.get(1).getTitle("en"));
                assertEquals("Konstgjort rev",
                    bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.get(1).getTitle("sv"));

            } catch (Exception e) {
                fail();
            }
        }, "y", "y");
    }

    @Test
    void testLongFilenameTruncationInInteraction() throws IOException {
        Path tempDir = Path.of(TEMP_DIR);
        Files.createDirectories(tempDir);

        String longFilenamePath = TEMP_DIR + "/" + LONG_FILENAME_CSV;
        Files.copy(Path.of(csvMetaFileCompleteEN), tempDir.resolve(LONG_FILENAME_CSV), REPLACE_EXISTING);

        String[] args = testCaseArgs("-u", "-md", longFilenamePath, "-mdL", "en", "-bv", String.valueOf(bvId));

        queueInteraction(() -> {
            new SymphonySetup(args);
            String output = displaceOut.toString();

            assertTrue(output.startsWith(
                    "Pending metadata import:")
            );
            assertTrue(output.contains(LONG_TRUNCATED_FILENAME_CSV),
                "Check that truncation works as expected for unusually long metadata filenames");

        }, "y");
    }
}
