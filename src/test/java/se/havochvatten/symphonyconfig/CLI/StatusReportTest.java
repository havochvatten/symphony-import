package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.model.Baseline;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StatusReportTest extends CliTestBase {
    private static final String fullReportResource = RESOURCES_PATH + "report/status-report-full-bilingual.txt";

    public StatusReportTest() {
        super(false);
    }

    @Test
    void statusReportTest() {
        String[] nationalAreasArgs = testCaseArgs(
            "-na", "BOUNDARY,TEST",
            "-naP", String.format("%s,%s", nationalAreaBoundary, nationalAreaSelectable),
            "-naC", "SWE");

        queueInteraction(() -> {
            new SymphonySetup(nationalAreasArgs);
        }, "y");

        queueInteraction(() -> {
            new SymphonySetup(installBilingualWithMatrixArgs());
            Integer bvId = getDbInterface().baselineVersionIdByName(TEST_BASELINE_NAME);
            assertNotNull(bvId);
            this.bvId = bvId;
        }, "y", "y", "y", "y", "y");

        String[] args = testCaseArgs("-s", "-v", "-bv", String.valueOf(bvId));
        queueInteraction(() -> {
            displaceOut.reset();
            new SymphonySetup(args);

            Baseline baseline = this.getDbInterface().getBaselineForReport(bvId);
            String expectedReport = getResourceFileAndReplace(fullReportResource,
                    Map.of(
                            "{BVER_ID}", String.valueOf(bvId),
                            "{SENSMX_ID}", String.valueOf(baseline.defaultMatrices.get(0).getId()),
                            "{CALCAREA1_ID}", String.valueOf(baseline.defaultCalcAreas.get(0).getId()),
                            "{CALCAREA2_ID}", String.valueOf(baseline.defaultCalcAreas.get(1).getId()))
            );
            String actualReport = displaceOut.toString().replace("\r\n", "\n"); // win-agnostic

            assertEquals(expectedReport, actualReport);
        });
    }

    // TODO: tests for partial, corrupted and inconsistent imports

    @AfterEach
    void cleanNationalAreas() {
        getDbInterface().cleanNationalAreas();
    }
}
