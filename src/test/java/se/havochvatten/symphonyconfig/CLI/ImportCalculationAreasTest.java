package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.config.CalcAreaImportSettings;
import se.havochvatten.symphonyconfig.setup.process.CalcAreaProcedure;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import se.havochvatten.symphonyconfig.setup.database.DbInterface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static se.havochvatten.symphonyconfig.setup.database.DbInterface.idHandler;

class ImportCalculationAreasTest extends CliTestBase {

    public ImportCalculationAreasTest() {
        super(true);
    }

    public static String getCalculationAreaByNameQueryStr(String schema) {
        return String.format(
            "SELECT ca.carea_id FROM %1$s.calculationarea ca " +
                "JOIN %1$s.capolygon cap ON cap_carea_id = ca.carea_id " +
            "WHERE ca.carea_name = ?", schema);
    }

    @Test
    void testImportDefaultCalculationAreas() {
        // cli arguments
        // -u    [update]
        // -caF  [calculation areas import]         ( local path to GeoPackage file )
        // -caDA [calulation areas - 'all default'] ( no-args option ) // set all areas contained in the given package as default

        String[] args = testCaseArgs(
            "-u",
            "-caF", calculationAreaPackage, "-caDA",
            "-bv", String.valueOf(bvId));

        try {
            getDbInterface().provideDummySensitivityMatrixForCalcArea(bvId, csvMatrixCompleteName);

            queueInteraction(() -> {
                new SymphonySetup(args);
                // utilize import procedure collect() method
                CalcAreaProcedure caProcedure =
                    new CalcAreaProcedure(
                        new CalcAreaImportSettings(null, calculationAreaPackage,"name",
                            false, true, null, Set.of(), Map.of())
                    );
                caProcedure.collect();

                for (CalcAreaProcedure.AreaMatrixTuple areaTuple : caProcedure.areaTuples) {
                    Integer carea =
                        dbInterface.query(
                            getCalculationAreaByNameQueryStr(dbSchema), idHandler, areaTuple.area().getAreaName());
                    assertNotNull(carea);
                }
            }, "y");
        } catch (SQLException sqlx) {
            fail(sqlx.getMessage());
        }
    }

    @Test
    void calculationAreasBindToTheirOwnBaselineMatrix() throws Exception {
        int otherBvId = 0;

        try {
            // Two baselines, each with a matrix of the SAME name. The other baseline's matrix is
            // created first so it lands on the LOWER sensm_id: an unscoped lookup that simply
            // returns the first matching row would pick that one instead of this baseline's own
            // matrix, so this ordering is what makes the test able to catch the defect.
            otherBvId = getDbInterface().installSecondaryBaselineVersion("SYM-712-other-baseline");
            int otherMatrixId = getDbInterface()
                .provideDummySensitivityMatrixForCalcArea(otherBvId, csvMatrixCompleteName);
            int ownMatrixId = getDbInterface()
                .provideDummySensitivityMatrixForCalcArea(bvId, csvMatrixCompleteName);

            assertNotEquals(otherMatrixId, ownMatrixId);

            String[] args = testCaseArgs("-u", "-bv", String.valueOf(bvId),
                "-caF", calculationAreaPackage, "-caDA");
            queueInteraction(() -> new SymphonySetup(args), "y");

            Integer bound = getDbInterface().query(String.format(
                "SELECT ca.carea_default_sensm_id FROM %s.calculationarea ca LIMIT 1", dbSchema),
                DbInterface.idHandler);

            assertEquals(ownMatrixId, bound,
                "Areas imported for a baseline must bind to that baseline's matrix, "
                    + "not to a same-named matrix on another baseline");
        } finally {
            getDbInterface().cleanCalculationAreas(bvId);
            if (otherBvId != 0) {
                getDbInterface().cleanBaselineVersion(otherBvId);
            }
        }
    }

    @AfterEach
    void cleanCalculationAreas() {
        getDbInterface().cleanCalculationAreas(bvId);
    }
}
