package se.havochvatten.symphonyconfig.CLI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;
import se.havochvatten.symphonyconfig.setup.config.CalcAreaImportSettings;
import se.havochvatten.symphonyconfig.setup.model.CalculationArea;
import se.havochvatten.symphonyconfig.setup.process.CalcAreaProcedure;

import java.sql.SQLException;

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
                                new CalcAreaImportSettings(null, calculationAreaPackage, "name", false, true, null)
                        );
                caProcedure.collect();

                for (CalculationArea area : caProcedure.areas) {
                    Integer carea = dbInterface.query(getCalculationAreaByNameQueryStr(dbSchema), idHandler, area.getAreaName());
                    assertNotNull(carea);
                }
            }, "y");
        } catch (SQLException sqlx) {
            fail(sqlx.getMessage());
        }
    }

    @AfterEach
    void cleanCalculationAreas() {
        getDbInterface().cleanCalculationAreas(bvId);
    }
}
