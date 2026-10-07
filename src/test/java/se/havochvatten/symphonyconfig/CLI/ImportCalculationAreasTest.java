package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.config.CalcAreaImportSettings;
import se.havochvatten.symphonyconfig.setup.process.CalcAreaProcedure;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import se.havochvatten.symphonyconfig.setup.database.DbInterface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                            false, null, Set.of(), Map.of())
                    );
                caProcedure.collect();

                for (CalcAreaProcedure.AreaMatrixTuple areaTuple : caProcedure.areaTuples) {
                    Integer carea =
                        dbInterface.query(
                            getCalculationAreaByNameQueryStr(dbSchema), idHandler, areaTuple.area().getAreaName());
                    assertNotNull(carea);
                }

                assertTrue(getDbInterface().countCalculationAreaPolygons(bvId) > 0,
                    "The import should have written calculation area polygons");
                assertEquals(0, getDbInterface().countCalculationAreaPolygonsWithoutGeometry(bvId),
                    "Every imported polygon needs a pg_polygon matching its GeoJSON: "
                        + "MSP-Symphony intersects scenario areas against that column");
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
                "SELECT ca.carea_default_sensm_id FROM %s.calculationarea ca WHERE ca.carea_name = ?", dbSchema),
                DbInterface.idHandler, "test-calc-area-1");

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

    @Test
    void importStoresMaxValueAndLeavesAbsentValueNull() throws Exception {
        getDbInterface().provideDummySensitivityMatrixForCalcArea(bvId, csvMatrixCompleteName);

        String[] args = testCaseArgs("-u", "-bv", String.valueOf(bvId),
            "-caF", calculationAreaPackageMaxValue, "-caD", "test-calc-area-calibrated");

        queueInteraction(() -> {
            SymphonySetup setup = new SymphonySetup(args);
            assertFalse(setup.hasFailed(), displaceErr.toString());
        }, "y");

        assertEquals(3098.8, getDbInterface().getCalculationAreaMaxValue(bvId, "test-calc-area-calibrated"));
        assertNull(getDbInterface().getCalculationAreaMaxValue(bvId, "test-calc-area-uncalibrated"),
            "An area without the attribute must stay uncalibrated, not get 0 or a neighbour's value");
        // This fixture is MultiPolygon-typed; testImportDefaultCalculationAreas covers the Polygon-typed package
        assertEquals(0, getDbInterface().countCalculationAreaPolygonsWithoutGeometry(bvId));
    }

    @Test
    void importRejectsUnusableMaxValuesAndWritesNoArea() throws Exception {
        getDbInterface().provideDummySensitivityMatrixForCalcArea(bvId, csvMatrixCompleteName);

        String[] args = testCaseArgs("-u", "-bv", String.valueOf(bvId),
            "-caF", calculationAreaPackageMaxValueFaulty, "-caDA");

        queueInteraction(() -> {
            SymphonySetup setup = new SymphonySetup(args);
            assertTrue(setup.hasFailed(), "An unusable maxValue must fail the import");
        }, "y");

        String errorOutput = displaceErr.toString();
        assertTrue(errorOutput.contains("maxValue"), errorOutput);
        // Text, decimal comma, zero, negative and blank: each must be named, none silently read
        for (String area : List.of("test-calc-area-text", "test-calc-area-comma", "test-calc-area-zero",
                                   "test-calc-area-negative", "test-calc-area-blank")) {
            assertTrue(errorOutput.contains(area), "The error should name '" + area + "': " + errorOutput);
        }
        assertEquals(0, getDbInterface().countCoupledCalculationAreas(bvId),
            "No calculation area may be written when any maxValue is unusable");
    }

    @AfterEach
    void cleanCalculationAreas() {
        getDbInterface().cleanCalculationAreas(bvId);
    }
}
