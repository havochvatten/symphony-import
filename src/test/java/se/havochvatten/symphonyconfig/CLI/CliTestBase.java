package se.havochvatten.symphonyconfig.CLI;

import com.github.stefanbirkner.systemlambda.Statement;
import org.junit.jupiter.api.AfterAll;
import se.havochvatten.symphonyconfig.TestBase;
import se.havochvatten.symphonyconfig.setup.model.Baseline;
import se.havochvatten.symphonyconfig.setup.model.NationalArea;
import se.havochvatten.symphonyconfig.setup.model.SymphonyBand;
import se.havochvatten.symphonyconfig.setup.model.SymphonyCategory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withTextFromSystemIn;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.havochvatten.symphonyconfig.CLI.ImportNationalAreasTest.getAllNatAreasForCountryCodeQuery;

public abstract class CliTestBase extends TestBase {

    private static final PrintStream standardOut = System.out;
    private static final PrintStream standardErr = System.err;
    protected static final String NEW_LINE = System.lineSeparator();

    protected final List<String> requiredArgs;

    protected final ByteArrayOutputStream displaceOut = new ByteArrayOutputStream();
    protected final ByteArrayOutputStream displaceErr = new ByteArrayOutputStream();

    public CliTestBase(boolean provideBaseline) {
        super(provideBaseline);
        requiredArgs = Arrays.asList("-db", database, "-dbU", dbUser, "-dbP", dbPassword);
        System.setOut(new PrintStream(displaceOut));
        System.setErr(new PrintStream(displaceErr));
    }

    protected String[] testCaseArgs(String... args) {
        ArrayList<String> caseArgs = new ArrayList<>(requiredArgs);
        caseArgs.addAll(Arrays.asList(args));
        return caseArgs.toArray(String[]::new);
    }

    public final String[] installBilingualWithMatrixArgs() {
        // cli arguments
        // -n    [install new baseline version]
        // -bvN  [new baseline version name]             // mandatory, unique
        // -bvD  [new baseline version description]      // non-mandatory
        // -bvV  [new baseline version valid to ]        // non-mandatory ISO 8601 date
        // -bvpE [new baseline version Ecosystems GeoTIFF] // mandatory valid local path
        // -bvpE [new baseline version Pressures GeoTIFF] // mandatory valid local path

        // -md  [metadata import] ( path )          // repeated argument
        // -mdL [metadata language] ( language )    // repeated arg (two languages)

        // -mx  [sensitivity matrix import] ( path )
        // -mxN [sensitivity matrix name/title]     // mandatory when mx import given
        // -mxL [sensitivity matrix language]
        return testCaseArgs("-n",
                "-bvN", TEST_BASELINE_NAME,
                "-bvD", TEST_BASELINE_DESC,
                "-bvV", TEST_VALIDTO_DATE,
                "-bvpE", TEST_TIFF_E_PATH,
                "-bvpP", TEST_TIFF_P_PATH,
                // complete bilingual metadata
                "-md", csvMetaFileCompleteSV,
                "-md", csvMetaFileCompleteEN,
                "-mdL", "sv", "en",
                // sensitivity matrix
                "-mx", csvMatrixFileEN,
                "-mxN", csvMatrixCompleteName,
                "-mxL", "en",
                // calculation areas
                "-caF",
                calculationAreaPackage,
                "-caDA"
        );
    }

    public void queueInteraction(Statement s, String ... input) {
        try {
            withTextFromSystemIn(input).execute(s);
        } catch (Exception e) {
            fail("A system-level error occurred:\n" + e.getMessage());
        }
    }

    protected void assertBilingualMatrixBaseline(Baseline bl) throws SQLException {
        assertEquals(4, bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.size());
        assertEquals(4, bl.getComponents().get(SymphonyCategory.PRESSURE).bands.size());

        for (SymphonyBand ecoBand : bl.getComponents().get(SymphonyCategory.ECOSYSTEM).bands.values()) {
            for (SymphonyBand prBand : bl.getComponents().get(SymphonyCategory.PRESSURE).bands.values()) {
                assertEquals(
                        dbInterface.readSensitivityValue("sv", ecoBand.getTitle("sv"), prBand.getTitle("sv"), csvMatrixCompleteName),
                        dbInterface.readSensitivityValue("en", ecoBand.getTitle("en"), prBand.getTitle("en"), csvMatrixCompleteName)
                );
            }
        }
    }

    protected void assertNationalAreasImportSuccess() throws IOException, SQLException {
        String expectedBoundaryJson = Files.readString(Path.of(nationalAreaBoundary));
        String expectedSelectableJson = Files.readString(Path.of(nationalAreaSelectable));

        List<NationalArea> nationalAreas = getDbInterface().query(
            getAllNatAreasForCountryCodeQuery(dbSchema), NationalArea.handler, "SWE");

        // check that the boundary row exists and that the 'areas' property equals the file contents
        NationalArea boundaryRow = nationalAreas.stream().filter(nationalArea -> nationalArea.getType().equals("BOUNDARY")).findFirst().orElse(null);
        NationalArea selectableRow = nationalAreas.stream().filter(nationalArea -> nationalArea.getType().equals("TEST")).findFirst().orElse(null);

        assertNotNull(boundaryRow);
        assertNotNull(selectableRow);

        assertNotNull(boundaryRow.getAreasJson());
        assertNotNull(selectableRow.getAreasJson());

        assertEquals(expectedBoundaryJson, boundaryRow.getAreasJson());
        assertEquals(expectedSelectableJson, selectableRow.getAreasJson());
    }

    @AfterAll
    public static void doLast() {
        System.setOut(standardOut);
        System.setErr(standardErr);
    }
}
