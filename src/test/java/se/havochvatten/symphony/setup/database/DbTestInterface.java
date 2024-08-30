package se.havochvatten.symphony.setup.database;

import org.apache.commons.dbutils.handlers.ScalarHandler;
import se.havochvatten.symphony.CLI.CliTestBase;
import se.havochvatten.symphony_setup.setup.database.DbInterface;
import se.havochvatten.symphony_setup.setup.model.MetaValue;
import se.havochvatten.symphony_setup.setup.model.SymphonyBand;
import se.havochvatten.symphony_setup.setup.model.SymphonyCategory;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import static java.sql.Statement.RETURN_GENERATED_KEYS;

public class DbTestInterface extends DbInterface {
    private static final String TEST_TIFF_E_PATH =
        CliTestBase.class.getResource("/baseline/symphony-import-test-BaselineE.tiff").getPath();
    private static final String TEST_TIFF_P_PATH =
        CliTestBase.class.getResource("/baseline/symphony-import-test-BaselineP.tiff").getPath();

    private static final String deleteBaselineVersionQuery =
        "DELETE FROM %s.baselineversion bv WHERE bv.bver_id = ?";
    private static final String getBaselineVersionIdSequenceQuery =
        "SELECT seq FROM (SELECT pg_get_serial_sequence('%s.baselineversion', 'bver_id') seq) res";
    private static final String resetBaselineVersionIdSequenceQuery =
        "ALTER SEQUENCE %s RESTART WITH %d";

    private static final ScalarHandler<String> stringHandler = new ScalarHandler<>();

    private Integer testBvId = null;

    public DbTestInterface(String database, String username, String password,
                           String schema, String port, String host) {
        super(database, username, password, schema, port, host);
    }

    public int installTestBaselineVersion() {
        if (testBvId == null) {
            String insertQuery = String.format("INSERT INTO %s.baselineversion " +
                "(bver_name, bver_desc, bver_validfrom, " +
                "bver_ecofilepath, bver_presfilepath, bver_locale) " +
                "VALUES ('TEST-Baseline', '', ?, ?, ?, 'en')", schema);

            String isoToday = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

            try (Connection conn = getConnection()) {
                PreparedStatement insertStmt =
                    conn.prepareStatement(insertQuery, RETURN_GENERATED_KEYS);

                insertStmt.setObject(1, isoToday, Types.DATE);
                insertStmt.setString(2, TEST_TIFF_E_PATH);
                insertStmt.setString(3, TEST_TIFF_P_PATH);

                insertStmt.executeUpdate();
                ResultSet rs = insertStmt.getGeneratedKeys();

                if (rs.next()) {
                    testBvId = rs.getInt(1);
                    insertStmt.close();
                } else {
                    insertStmt.close();
                    throw new SQLException("Failure inserting baseline version for test");
                }

            } catch (SQLException e) {
                throw new RuntimeException("Database transaction error");
            }
        }

        return testBvId;
    }

    public void cleanTestBaselineVersion() {
        try (Connection conn = getConnection()) {

            clearBandData(testBvId);

            // Delete dummy baseline
            qr.update(conn,
                String.format(deleteBaselineVersionQuery, schema),
                testBvId);

            // Get IDENTITY sequence literal
            String idSequence = qr.query(conn,
                String.format(getBaselineVersionIdSequenceQuery, schema), stringHandler);

            if(!idSequence.isEmpty()) {
                qr.execute(conn,
                    String.format(resetBaselineVersionIdSequenceQuery, idSequence, testBvId));
            } else {
                // Would be a weird error. Should throw?
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error cleaning up baseline version for test", e);
        }
    }
}
