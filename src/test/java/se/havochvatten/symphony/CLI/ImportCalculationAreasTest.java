package se.havochvatten.symphony.CLI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.havochvatten.symphony_setup.setup.SymphonySetup;
import se.havochvatten.symphony_setup.setup.config.CalcAreaImportSettings;
import se.havochvatten.symphony_setup.setup.model.CalculationArea;
import se.havochvatten.symphony_setup.setup.process.CalcAreaProcedure;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.havochvatten.symphony_setup.setup.database.DbInterface.idHandler;

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
    }

    @AfterEach
    void cleanCalculationAreas() {
        getDbInterface().cleanCalculationAreas();
    }
}
