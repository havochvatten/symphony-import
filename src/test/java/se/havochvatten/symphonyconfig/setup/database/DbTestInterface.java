package se.havochvatten.symphonyconfig.setup.database;

import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static java.sql.Statement.RETURN_GENERATED_KEYS;
import static se.havochvatten.symphonyconfig.TestBase.*;

public class DbTestInterface extends DbInterface {
    private static final String BASELINE_EXTENT_POLY_PATH ="/baseline/test-baseline-extent.json";
    public static final String DEFAULT_TEST_BASELINE_NAME = "TEST-Baseline";

    private static final String deleteBaselineVersionStatement =
        "DELETE FROM %s.baselineversion bv WHERE bv.bver_id = ?";
    private static final String deleteSensitivityMatricesStatement =
        "DELETE FROM %s.sensitivitymatrix WHERE sensm_bver_id = ?";
    private static final String deleteNationalAreasStatement =
        "DELETE FROM %s.nationalarea WHERE true";
    private static final String deleteCalculationAreasStatement =
        "DELETE FROM %1$s.calculationarea ca " +
            "USING %1$s.sensitivitymatrix sm " +
        "WHERE ca.carea_default_sensm_id = sm.sensm_id " +
          "AND sm.sensm_bver_id = ?";
    private static final String deleteCalculationAreaPolygonsStatement =
        "DELETE FROM %1$s.capolygon cap " +
            "USING %1$s.calculationarea ca, %1$s.sensitivitymatrix sm " +
        "WHERE cap.cap_carea_id = ca.carea_id " +
          "AND ca.carea_default_sensm_id = sm.sensm_id " +
          "AND sm.sensm_bver_id = ?";

    private static final String getBaselineVersionIdSequenceQuery =
        "SELECT seq FROM (SELECT pg_get_serial_sequence('%s.baselineversion', 'bver_id') seq) res";
    private static final String resetSequenceStatement =
        "ALTER SEQUENCE %s RESTART WITH %d";

    private static String provideDummySensitivityMatrixForCalcAreaStatement(String schema) {
        return String.format("INSERT INTO %s.sensitivitymatrix (sensm_name, sensm_bver_id, sensm_owner) VALUES (?, ?, NULL)", schema);
    }

    private static final ScalarHandler<String> stringHandler = new ScalarHandler<>();

    private Integer testBvId = null;
    private Integer testCalcAreaId = null;

    public static String getAllNatAreasForCountryCodeQuery(String schema) {
        return String.format("SELECT narea_id, narea_type, narea_areas, narea_countryiso3 " +
            "FROM %s.nationalarea WHERE narea_countryiso3 = ?", schema);
    }

    public String sensitivityControlQuery(String isoLang, String ecoTitle, String prTitle, String mxName) {
        return MessageFormat.format("SELECT sens_value FROM {0}.sensitivity s " +
            "JOIN {0}.sensitivitymatrix sm ON sm.sensm_id = s.sens_sensm_id " +
            "JOIN {0}.meta_bands mbe ON mbe.metaband_id = s.sens_eco_band_id " +
            "JOIN {0}.meta_bands mbp ON mbp.metaband_id = s.sens_pres_band_id " +
            "JOIN {0}.meta_values mbev ON mbev.metaval_band_id = mbe.metaband_id " +
            "JOIN {0}.meta_values mbpv ON mbpv.metaval_band_id = mbp.metaband_id " +
            "WHERE sm.sensm_name = ''{4}'' " +
            "AND mbev.metaval_language = ''{1}'' AND mbev.metaval_field = ''title'' " +
            "AND mbpv.metaval_language = ''{1}'' AND mbpv.metaval_field = ''title'' " +

            "AND   mbev.metaval_value = ''{2}'' " +
            "AND   mbpv.metaval_value = ''{3}''", schema, isoLang, ecoTitle, prTitle, mxName);
    }

    public Double readSensitivityValue(String isoLang, String ecoTitle, String prTitle, String mxName)
        throws SQLException {
        return getFirstDoubleValueByQuery(
            sensitivityControlQuery(isoLang, ecoTitle, prTitle, mxName)
        );
    }

    public DbTestInterface(String database, String username, String password,
                           String schema, String port, String host) {
        super(database, username, password, schema, port, host);
    }

    public int installTestBaselineVersion() {
        if (testBvId == null) {
            String insertQuery = String.format("INSERT INTO %s.baselineversion " +
                "(bver_name, bver_desc, bver_validfrom, " +
                "bver_ecofilepath, bver_presfilepath, bver_locale) " +
                "VALUES ('%s', '', ?, ?, ?, 'en')", schema, DEFAULT_TEST_BASELINE_NAME);

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

    public int provideDummySensitivityMatrixForCalcArea(int bvId, String matrixName) throws SQLException {
        return qr.insert(getConnection(), provideDummySensitivityMatrixForCalcAreaStatement(schema), idHandler, matrixName, bvId);
    }

    public int installDummyCalculationArea(int matrixId, boolean makeDefault) {
        if (testBvId == null) {
            throw new IllegalStateException("Fatal error: test baseline version not installed.");
        }

        if (testCalcAreaId == null) {
            try (Connection conn = getConnection()) {
                String insertAreaQuery = String.format("INSERT INTO %s.calculationarea " +
                        "(carea_name, carea_default, carea_default_sensm_id, " +
                        "carea_maxvalue, carea_atype_id) " +
                        "VALUES ('TEST-CalculationArea', %s, ?, NULL, NULL)",
                    schema, makeDefault ? "true" : "false");

                String polygonDef = IOUtils.resourceToString(BASELINE_EXTENT_POLY_PATH, StandardCharsets.UTF_8);

                PreparedStatement insertStmt =
                    conn.prepareStatement(insertAreaQuery, RETURN_GENERATED_KEYS);

                insertStmt.setObject(1, matrixId, Types.INTEGER);

                insertStmt.executeUpdate();
                ResultSet rs = insertStmt.getGeneratedKeys();

                if (rs.next()) {
                    testCalcAreaId = rs.getInt(1);
                    insertStmt.close();

                    String insertPolygonQuery =
                        String.format("INSERT INTO %s.capolygon (cap_carea_id, cap_polygon) VALUES " +
                            "(%d, '%s')", schema, testCalcAreaId, polygonDef);

                    conn.createStatement().execute(insertPolygonQuery);

                } else {
                    insertStmt.close();
                    throw new SQLException("Failure inserting dummy calculation area");
                }

            } catch (SQLException e) {
                throw new RuntimeException("Database transaction error");
            } catch (IOException ioe) {
                throw new RuntimeException("Test resource access error");
            }
        }

        return testCalcAreaId;
    }

    public Double getFirstDoubleValueByQuery(String query) throws SQLException {
        Connection conn = getConnection();
        PreparedStatement stmt = conn.prepareStatement(query);

        ResultSet result = stmt.executeQuery();

        if (result.next()) {
            return result.getDouble(1);
        } else {
            return null;
        }
    }

    public void cleanBaselineVersion(int bvId) {
        try (Connection conn = getConnection()) {

            clearBandData(bvId);

            // Delete calculation areas
            qr.update(conn,
                String.format(deleteCalculationAreaPolygonsStatement, schema), bvId);
            qr.update(conn,
                String.format(deleteCalculationAreasStatement, schema), bvId);

            // Delete sensitivity matrix
            qr.update(conn,
                String.format(deleteSensitivityMatricesStatement, schema),
                bvId);

            // Delete dummy baseline
            qr.update(conn,
                String.format(deleteBaselineVersionStatement, schema),
                bvId);

            // Get IDENTITY sequence literal
            String idSequence = qr.query(conn,
                String.format(getBaselineVersionIdSequenceQuery, schema), stringHandler);

            if(!idSequence.isEmpty()) {
                qr.execute(conn,
                    String.format(resetSequenceStatement, idSequence, bvId));
            } else {
                // Would be a weird error. Should throw?
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error cleaning up baseline version for test", e);
        }
    }

    public void cleanTestBaselineVersion() {
        cleanBaselineVersion(testBvId);
    }

    public void cleanNationalAreas() {
        try (Connection conn = getConnection()) {
            // Delete national areas
            qr.update(conn,
                String.format(deleteNationalAreasStatement, schema));
        } catch (SQLException e) {
            throw new RuntimeException("Error purging national areas table", e);
        }
    }

    public Integer getBaselineVersionByName(String name) {
        try {
            return query(
                String.format("SELECT bver_id FROM %s.baselineversion WHERE bver_name = ?", schema),
                idHandler, name);
        } catch (SQLException e) {
            throw new RuntimeException("Error querying baseline version by name", e);
        }
    }

    /** Insert a reliability partition polygon bound to the first band of the given baseline. */
    public void installReliabilityPartition(int bvId) throws SQLException {
        qr.update(getConnection(), String.format(
            "INSERT INTO %1$s.reliabilitypartition (rp_metaband_id, rp_value, rp_polygon) "
                + "SELECT metaband_id, 3, "
                + "ST_Multi(ST_GeomFromText('POLYGON((0 0,1 0,1 1,0 1,0 0))',4326)) "
                + "FROM %1$s.meta_bands WHERE metaband_bver_id = ? LIMIT 1", schema), bvId);
    }

    public void cleanReliabilityPartitions(int bvId) throws SQLException {
        qr.update(getConnection(), String.format(
            "DELETE FROM %1$s.reliabilitypartition rp USING %1$s.meta_bands mb "
                + "WHERE mb.metaband_id = rp.rp_metaband_id AND mb.metaband_bver_id = ?", schema), bvId);
    }

    /** Mark an existing matrix as user-owned, simulating a matrix created through the GUI. */
    public void setMatrixOwner(String matrixName, String owner) throws SQLException {
        qr.update(getConnection(), String.format(
            "UPDATE %s.sensitivitymatrix SET sensm_owner = ? WHERE sensm_name = ?", schema),
            owner, matrixName);
    }

    public int countMetaValues(int bvId) throws SQLException {
        Long n = query(String.format(
            "SELECT count(*) FROM %1$s.meta_values mv JOIN %1$s.meta_bands mb "
                + "ON mb.metaband_id = mv.metaval_band_id WHERE mb.metaband_bver_id = ?", schema),
            longHandler, bvId);
        return n == null ? 0 : n.intValue();
    }

    public void cleanCalculationAreas(int bvId) {
        try (Connection conn = getConnection()) {
            // Delete calculation areas
            qr.update(conn,
                String.format(deleteCalculationAreaPolygonsStatement, schema), bvId);
            qr.update(conn,
                String.format(deleteCalculationAreasStatement, schema), bvId);
        } catch (SQLException e) {
            throw new RuntimeException("Error purging calculation areas", e);
        }
    }
}
