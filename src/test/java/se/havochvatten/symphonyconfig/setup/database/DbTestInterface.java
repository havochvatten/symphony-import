package se.havochvatten.symphonyconfig.setup.database;

import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

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
            testBvId = installBaselineVersion(DEFAULT_TEST_BASELINE_NAME, LocalDate.now());
        }
        return testBvId;
    }

    /**
     * Installs a second, independently-named baseline version, distinct from the cached
     * {@link #installTestBaselineVersion()} fixture. Unlike that method, this one is never cached:
     * every call inserts a new row and returns its own generated id. Callers must clean it up
     * themselves via {@link #cleanBaselineVersion(int)}.
     */
    public int installSecondaryBaselineVersion(String name) {
        // A day before the primary fixture's 'today', avoiding collision
        return installBaselineVersion(name, LocalDate.now().minus(1, ChronoUnit.DAYS));
    }

    private int installBaselineVersion(String name, LocalDate validFrom) {
        String insertQuery = String.format("INSERT INTO %s.baselineversion " +
            "(bver_name, bver_desc, bver_validfrom, " +
            "bver_ecofilepath, bver_presfilepath, bver_locale) " +
            "VALUES ('%s', '', ?, ?, ?, 'en')", schema, name);

        String isoValidFrom = validFrom.format(DateTimeFormatter.ISO_DATE);

        try (Connection conn = getConnection()) {
            PreparedStatement insertStmt =
                conn.prepareStatement(insertQuery, RETURN_GENERATED_KEYS);

            insertStmt.setObject(1, isoValidFrom, Types.DATE);
            insertStmt.setString(2, TEST_TIFF_E_PATH);
            insertStmt.setString(3, TEST_TIFF_P_PATH);

            insertStmt.executeUpdate();
            ResultSet rs = insertStmt.getGeneratedKeys();

            if (rs.next()) {
                int newBvId = rs.getInt(1);
                insertStmt.close();
                return newBvId;
            } else {
                insertStmt.close();
                throw new SQLException("Failure inserting secondary baseline version for test");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Database transaction error");
        }
    }

    public int provideDummySensitivityMatrixForCalcArea(int bvId, String matrixName) throws SQLException {
        return qr.insert(getConnection(), provideDummySensitivityMatrixForCalcAreaStatement(schema), idHandler, matrixName, bvId);
    }

    /**
     * Inserts a calculation area with one polygon, owned by whichever baseline version owns
     * the given matrix (calculationarea.carea_default_sensm_id is the ownership relation).
     * Never cached: every call inserts a new row and returns its own generated id, so a test
     * can install areas on two baseline versions at once.
     */
    public int installCalculationArea(String name, int matrixId, boolean makeDefault) {
        try (Connection conn = getConnection()) {
            String insertAreaQuery = String.format("INSERT INTO %s.calculationarea " +
                    "(carea_name, carea_default, carea_default_sensm_id, " +
                    "carea_maxvalue, carea_atype_id) " +
                    "VALUES (?, %s, ?, NULL, NULL)",
                schema, makeDefault ? "true" : "false");

            String polygonDef = IOUtils.resourceToString(BASELINE_EXTENT_POLY_PATH, StandardCharsets.UTF_8);

            PreparedStatement insertStmt =
                conn.prepareStatement(insertAreaQuery, RETURN_GENERATED_KEYS);

            insertStmt.setString(1, name);
            insertStmt.setObject(2, matrixId, Types.INTEGER);

            insertStmt.executeUpdate();
            ResultSet rs = insertStmt.getGeneratedKeys();

            if (!rs.next()) {
                insertStmt.close();
                throw new SQLException("Failure inserting calculation area for test");
            }

            int areaId = rs.getInt(1);
            insertStmt.close();

            String insertPolygonQuery =
                String.format("INSERT INTO %s.capolygon (cap_carea_id, cap_polygon) VALUES " +
                    "(%d, '%s')", schema, areaId, polygonDef);

            conn.createStatement().execute(insertPolygonQuery);

            return areaId;

        } catch (SQLException e) {
            throw new RuntimeException("Database transaction error", e);
        } catch (IOException ioe) {
            throw new RuntimeException("Test resource access error", ioe);
        }
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

    /** Count calculation areas flagged 'default' for the sensitivity matrices of the given baseline. */
    public int countDefaultCalculationAreas(int bvId) throws SQLException {
        Long n = query(String.format(
            "SELECT count(*) FROM %1$s.calculationarea ca "
                + "JOIN %1$s.sensitivitymatrix m ON m.sensm_id = ca.carea_default_sensm_id "
                + "WHERE m.sensm_bver_id = ? AND ca.carea_default", schema), longHandler, bvId);
        return n == null ? 0 : n.intValue();
    }

    /**
     * Whether the named calculation area, on the given baseline's sensitivity matrices, is flagged
     * as the default area. Scoped by name (not just counted) so a test can assert identity: which
     * area is default, not merely how many are.
     */
    public boolean isCalculationAreaDefault(int bvId, String careaName) throws SQLException {
        Boolean isDefault = query(String.format(
            "SELECT ca.carea_default FROM %1$s.calculationarea ca "
                + "JOIN %1$s.sensitivitymatrix m ON m.sensm_id = ca.carea_default_sensm_id "
                + "WHERE m.sensm_bver_id = ? AND ca.carea_name = ?", schema),
            new ScalarHandler<Boolean>(), bvId, careaName);
        return Boolean.TRUE.equals(isDefault);
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

    /** Adds a secondary calcareasensmatrix coupling, as the GUI does when an area gains a matrix. */
    public void linkCalculationAreaToMatrix(int areaId, int matrixId) throws SQLException {
        qr.update(getConnection(), String.format(
            "INSERT INTO %s.calcareasensmatrix (casen_carea_id, casen_sensm_id) VALUES (?, ?)",
            schema), areaId, matrixId);
    }

    public boolean calculationAreaExists(int areaId) throws SQLException {
        Long n = query(String.format(
            "SELECT count(*) FROM %s.calculationarea WHERE carea_id = ?", schema),
            longHandler, areaId);
        return n != null && n > 0;
    }

    /**
     * Removes one calculation area with its links and polygons, in foreign key order. Tests that
     * install an area on a second baseline call this before cleaning that baseline, so a failure
     * mid-test cannot leave a link row that then blocks cleanup on casen_carea_fk.
     */
    public void deleteCalculationArea(int areaId) {
        try (Connection conn = getConnection()) {
            qr.update(conn, String.format(
                "DELETE FROM %s.calcareasensmatrix WHERE casen_carea_id = ?", schema), areaId);
            qr.update(conn, String.format(
                "DELETE FROM %s.capolygon WHERE cap_carea_id = ?", schema), areaId);
            qr.update(conn, String.format(
                "DELETE FROM %s.calculationarea WHERE carea_id = ?", schema), areaId);
        } catch (SQLException e) {
            throw new RuntimeException("Error removing test calculation area", e);
        }
    }
}
