package se.havochvatten.symphony.CLI;

import org.junit.jupiter.api.Test;
import se.havochvatten.symphony_setup.setup.SymphonySetup;
import se.havochvatten.symphony_setup.setup.model.Baseline;
import se.havochvatten.symphony_setup.setup.model.SymphonyCategory;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateTest extends CliTestBase {

    @Test
    public void invokeWithoutBaselineVersionAndAbort() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path to import )            // repeatable argument
        // -mdL [metadata language] ( ISO-639 language code )   // repeatable argument like above
        // -bv  [baseline version database id] (omitted in this case ,
        //                                      should trigger the interaction tested below)

        String[] args = testCaseArgs("-u", "-md", csvFilePartial, "-mdL", "sv");

        // queue input to abort: ('a')
        queueInteraction(() -> {
            new SymphonySetup(args);

            String output = displaceOut.toString();

            assertTrue(output.startsWith(
                "Update procedure invoked without specifying baseline version id."
            ));
            assertTrue(output.endsWith(
                "Update aborted interactively." + NEW_LINE
            ));
        }, "a");
    }

    @Test
    public void invokeWithBaselineVersionAndAbort() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path to import )            // repeatable argument
        // -mdL [metadata language] ( ISO-639 language code )   // repeatable argument like above
        // -bv  [baseline version database id]

        String[] args = testCaseArgs( "-u", "-md", csvFilePartial, "-mdL", "sv", "-bv", String.valueOf(bvId));

        // queue input to abort import: (any key but 'y')
        queueInteraction(() -> {

            new SymphonySetup(args);

            assertTrue(displaceOut.toString().startsWith(
                "Pending metadata import:")
            );

            try {
                Baseline bl = getDbInterface().getBaseline(bvId);

                // assert import was aborted: no band information was added (expect 0)
                assertEquals(0, bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.keySet().size());
                assertEquals(0, bl.getComponents().get(SymphonyCategory.PRESSURE).bands.keySet().size());

            } catch (Exception e) {
                fail();
            }
        }, "a");
    }

    @Test
    public void invokeWithBaselineVersionAndImportPartialCSV() throws Exception {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path to import )            // repeatable argument
        // -mdL [metadata language] ( ISO-639 language code )   // repeatable argument like above
        // -bv  [baseline version database id]

        String[] args = testCaseArgs("-u", "-md", csvFilePartial, "-mdL", "sv", "-bv", String.valueOf(bvId));

        // queue input to commence import procedure ('y')
        queueInteraction(() -> {
            new SymphonySetup(args);

            assertEquals(displaceOut.toString().substring(0, 24), "Pending metadata import:");
            assertTrue(displaceOut.toString().startsWith(
                "Pending metadata import:")
            );

            try {
                Baseline bl = getDbInterface().getBaseline(bvId);

                //
                assertEquals(4, bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.keySet().size());
                assertEquals(3, bl.getComponents().get(SymphonyCategory.PRESSURE).bands.keySet().size());

                assertTrue(bl.isIncomplete());

            } catch (Exception e) {
                fail();
            }
        }, "y");
    }

    @Test
    public void invokeWithBaselineVersionAndImportPartialExcel() throws Exception {
        // cli arguments
        // -u   [update]
        // -md  [metadata import]   ( path )
        // -mdL [metadata language] ( language )
        // -bv  [baseline version database id]

        String[] args = testCaseArgs("-u", "-md", excelFilePartial, "-mdL", "sv", "-bv", String.valueOf(bvId));

        // queue input to commence import procedure ('y')
        queueInteraction(() -> {
            new SymphonySetup(args);

            assertEquals(displaceOut.toString().substring(0, 24), "Pending metadata import:");
            assertTrue(displaceOut.toString().startsWith(
                "Pending metadata import:")
            );

            try {
                Baseline bl = getDbInterface().getBaseline(bvId);

                //
                assertEquals(4, bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.keySet().size());
                assertEquals(3, bl.getComponents().get(SymphonyCategory.PRESSURE).bands.keySet().size());

                assertTrue(bl.isIncomplete());

            } catch (Exception e) {
                fail();
            }
        }, "y");
    }

    @Test
    public void invokeWithMultipleImportFormats() {
        // cli arguments
        // -u   [update]
        // -md  [metadata import] ( path ) -md ( path )  // repeatable argument
        // -mdL [metadata language] ( language )         // single argument for multiple files: will fall back to last
        // -bv  [baseline version database id]

        String[] args = testCaseArgs("-u",
            "-md", xlsxFilePartialEco,
            "-md", csvFilePartialPressure,
            "-mdL", "sv", "-bv", String.valueOf(bvId));

        // queue input to commence import procedure (2x 'y' to confirm both files)
        queueInteraction(() -> {
            new SymphonySetup(args);

            assertEquals(displaceOut.toString().substring(0, 70),
                "Notice:\n" +
                    "Language parameter missing for the 2nd provided metadata file.");

            try {
                Baseline bl = getDbInterface().getBaseline(bvId);

                assertEquals(4, bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.keySet().size());
                assertEquals(4, bl.getComponents().get(SymphonyCategory.PRESSURE).bands.keySet().size());

                assertFalse(bl.isIncomplete());

            } catch (Exception e) {
                fail();
            }
        }, "y", "y");
    }
}
