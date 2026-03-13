package se.havochvatten.symphony.CLI;

import com.github.stefanbirkner.systemlambda.Statement;
import org.junit.jupiter.api.AfterAll;
import se.havochvatten.symphony.TestBase;
import se.havochvatten.symphony_setup.setup.model.Baseline;
import se.havochvatten.symphony_setup.setup.model.SymphonyBand;
import se.havochvatten.symphony_setup.setup.model.SymphonyCategory;

import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withTextFromSystemIn;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    protected String[] testCaseArgs(String ...args) {
        ArrayList<String> caseArgs = new ArrayList<>(requiredArgs);
        caseArgs.addAll(Arrays.asList(args));
        return caseArgs.toArray(String[]::new);
    }

    public void queueInteraction(Statement s, String ... input) {
        try {
            withTextFromSystemIn(input).execute(s);
        } catch (Exception e) {
            // exotic IO error
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

    @AfterAll
    public static void doLast() {
        System.setOut(standardOut);
        System.setErr(standardErr);
    }
}
