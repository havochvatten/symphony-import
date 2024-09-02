package se.havochvatten.symphony.CLI;

import com.github.stefanbirkner.systemlambda.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import se.havochvatten.symphony.TestBase;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withTextFromSystemIn;

public abstract class CliTestBase extends TestBase {

    private static final PrintStream standardOut = System.out;
    protected static final String NEW_LINE = System.lineSeparator();

    protected final List<String> requiredArgs;

    protected final ByteArrayOutputStream displaceOut = new ByteArrayOutputStream();

    public CliTestBase() {
       super();
        requiredArgs = Arrays.asList("-db", database, "-dbU", dbUser, "-dbP", dbPassword);
        System.setOut(new PrintStream(displaceOut));
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

    @AfterEach
    public void tearDown() {
        getDbInterface().cleanTestBaselineVersion();
    }

    @AfterAll
    public static void doLast() {
        System.setOut(standardOut);
    }
}
