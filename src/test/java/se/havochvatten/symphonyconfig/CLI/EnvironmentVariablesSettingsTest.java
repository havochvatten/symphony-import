package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static se.havochvatten.symphonyconfig.setup.SymphonySetup.*;
import static se.havochvatten.symphonyconfig.setup.database.DbTestInterface.DEFAULT_TEST_BASELINE_NAME;

@ExtendWith(SystemStubsExtension.class)
class EnvironmentVariablesSettingsTest extends CliTestBase {

    @SystemStub
    private EnvironmentVariables environmentVariables;

    private static final String EXPECTED_REPORT_HEAD = "Baseline version id: %s%nBaseline version name: %s";
    private String expectedReportString;

    public EnvironmentVariablesSettingsTest() {
        super(true);
    }

    @BeforeEach
    public void setUp() {
        expectedReportString = String.format(EXPECTED_REPORT_HEAD, bvId, DEFAULT_TEST_BASELINE_NAME);
    }

    @Test
    void testProvideDbSettingsAsEnvVars() {
        environmentVariables.set(DEFAULT_DB_NAME_ENV, database);
        environmentVariables.set(DEFAULT_DB_USER_ENV, dbUser);
        environmentVariables.set(DEFAULT_DB_PASSWORD_ENV, dbPassword);

        queueInteraction(() -> {
            new SymphonySetup(new String[]{ "-dbH", dbHost, "-s", "-bv", String.valueOf(bvId) });

            assertTrue(displaceOut.toString().startsWith(expectedReportString));
        });
    }

    @Test
    void testProvideDbSettingsAsCustomEnvVars() {
        String myCustomEnvVariable = "myCustomEnvVariable";

        environmentVariables.set(myCustomEnvVariable, database);
        environmentVariables.set(DEFAULT_DB_USER_ENV, dbUser);
        environmentVariables.set(DEFAULT_DB_PASSWORD_ENV, dbPassword);

        queueInteraction(() -> {
            new SymphonySetup(new String[]{
                "-dbH", dbHost,
                "-envDb", myCustomEnvVariable,
                "-s", "-bv", String.valueOf(bvId)
            });

            assertTrue(displaceOut.toString().startsWith(expectedReportString));
        });
    }
}
