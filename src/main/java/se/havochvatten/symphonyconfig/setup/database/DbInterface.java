package se.havochvatten.symphonyconfig.setup.database;

import org.apache.commons.cli.ParseException;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ArrayHandler;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.geotools.geojson.geom.GeometryJSON;
import se.havochvatten.symphonyconfig.setup.model.*;
import se.havochvatten.symphonyconfig.setup.process.CalcAreaProcedure;
import se.havochvatten.symphonyconfig.setup.process.MatrixBase;
import se.havochvatten.symphonyconfig.setup.process.MetadataBase;
import se.havochvatten.symphonyconfig.setup.process.NationalAreaRowInsert;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static se.havochvatten.symphonyconfig.setup.model.DbMatrix.combinationsQuery;
import static se.havochvatten.symphonyconfig.setup.model.DbMatrix.missingBandNumbersQuery;
import static se.havochvatten.symphonyconfig.setup.model.DbNationalArea.allAreasCheapQuery;
import static se.havochvatten.symphonyconfig.setup.model.NationalArea.areaTypesExclusiveQuery;
import static se.havochvatten.symphonyconfig.setup.process.NationalAreaRowInsert.TYPE_BOUNDARY;

public class DbInterface {

    private final String host;
    private final String database;
    private final String username;
    private final String password;

    protected final String schema;
    private final int port;
    private static final int DEFAULT_PORT = 5432;
    private static final String DEFAULT_SCHEMA = "symphony";
    protected final QueryRunner qr = new QueryRunner();

    private Connection activeConnection = null;
    public static final ScalarHandler<Long> longHandler = new ScalarHandler<>();
    public static final ScalarHandler<Integer> idHandler = new ScalarHandler<>();
    // assuming integer id is present in first column
    public static final ColumnListHandler<Integer> idListHandler = new ColumnListHandler<>();

    static final GeometryJSON json = new GeometryJSON();

    protected Connection getConnection() throws SQLException {
        if (activeConnection == null || activeConnection.isClosed()) {
            String connectionString = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("search_path", schema);

            activeConnection = DriverManager.getConnection(connectionString, props);
        }

        return activeConnection;
    }

    public DbInterface(String database, String username, String password,
                       String schema, String port, String host) {
        this.database =         database;
        this.username =         username;
        this.password =         password;
        this.schema =           schema == null ? DEFAULT_SCHEMA : schema;
        this.port =             port == null ? DEFAULT_PORT : Integer.parseInt(port);
        this.host =             host == null ? "localhost" : host;
    }

    /**
     * @param baselineVersionId The version ID [bver_id] of the requested Baseline, or <i>null</i>.
     * <br>Pass <i>null</i> to return currently active baseline.
     *
     * @return BaselineVersion
     * @throws SQLException Inner QueryRunner.query(...) invocation may throw
     */
    public BaselineVersion getBaselineVersion(@Nullable Integer baselineVersionId) throws SQLException {

        Connection conn = getConnection();

        String baselineQuery =
            baselineVersionId == null ?
                BaselineVersion.selectLatestQuery(this.schema) :
                BaselineVersion.selectSpecificQuery(this.schema, baselineVersionId);

        List<BaselineVersion> baseLineSet = qr.query(conn, baselineQuery, BaselineVersion.handler);

        if (baseLineSet.isEmpty()) {
            return null;
        }

        return baseLineSet.get(0);
    }

    public Integer baselineVersionIdByName(String bvName) throws SQLException {
        return query(String.format("SELECT bver_id from %s.baselineversion WHERE bver_name = ?", this.schema), idHandler,
            bvName);
    }

    /**
     * MSP-Symphony resolves the current baseline by validFrom and throws
     * BASELINE_VERSION_MULT_MATCHES when more than one baseline version shares a date.
     */
    public boolean baselineVersionExistsForDate(java.sql.Date validFrom) throws SQLException {
        Long n = query(String.format(
            "SELECT count(*) FROM %s.baselineversion WHERE bver_validfrom = ?", schema),
            longHandler, validFrom);
        return n != null && n > 0;
    }

    public int[] getAvailableBaselineVersionIds() throws SQLException {
        return query(BaselineVersion.selectAvailableVersions(this.schema), idListHandler)
                        .stream().mapToInt(Integer::valueOf).toArray();
    }

    public Set<Integer> getAvailableAreaTypes() throws SQLException {
        return new HashSet<>(
            query(AreaType.getAvailableAreaTypeIdsQuery(this.schema), idListHandler)
        );
    }

    public int[] getAvailableCalculationAreaIds() throws SQLException {
        return query(String.format("SELECT carea_id FROM %s.calculationarea", this.schema), idListHandler)
                        .stream().mapToInt(Integer::valueOf).toArray();
    }

    public Baseline getBaseline(Integer baselineVersionId) throws Exception {
        try {
            Connection conn = getConnection();
            Baseline currentBaseline = new Baseline(getBaselineVersion(baselineVersionId));

            int bvId = currentBaseline.getVersion().getId();

            SymphonyBand[] ecoBands = qr.query(conn,
                SymphonyBand.selectQuery(this.schema, bvId, SymphonyCategory.ECOSYSTEM),
                                         SymphonyBand.handler).toArray(SymphonyBand[]::new),
                           pressureBands = qr.query(conn,
                SymphonyBand.selectQuery(this.schema, bvId, SymphonyCategory.PRESSURE),
                                         SymphonyBand.handler).toArray(SymphonyBand[]::new);

            currentBaseline.collectComponents(ecoBands, pressureBands);

            for (SymphonyCategory layerType : SymphonyCategory.values()) {
                MetaValue[] meta = qr.query(conn, MetaValue.selectQuery(schema, currentBaseline.version.getId(), layerType),
                                            MetaValue.handler).toArray(MetaValue[]::new);
                currentBaseline.collectBandMeta(layerType, meta);
            }

            return currentBaseline;
        } catch (SQLException sqlException) {
            throw new Exception("Error retrieving baseline data. Aborting");
        }
    }

    public Baseline getBaselineForReport(Integer baselineVersionId) throws Exception {
        Baseline baseline = getBaseline(baselineVersionId);

        try  {
            List<Integer> matrixIds = getMatrixIdsForBaselineVersion(baselineVersionId);
            baseline.setAllMatrixIds(matrixIds);
            baseline.setDefaultCalcAreas(
                getCalcAreasForMatrixIds(matrixIds.stream().mapToInt(Integer::valueOf).toArray())
            );
            baseline.setDefaultMatrices(
                getMatricesById(baseline.getDefaultCalcAreas().stream().mapToInt(DbCalculationArea::getDefaultSensitivityMatrixId).toArray())
            );

            for (DbMatrix matrix : baseline.getDefaultMatrices()) {
                matrix.setMissingEcoBands(
                    query(missingBandNumbersQuery(schema, SymphonyCategory.ECOSYSTEM), idListHandler, baselineVersionId, matrix.getId())
                );
                matrix.setMissingPressureBands(
                    query(missingBandNumbersQuery(schema, SymphonyCategory.PRESSURE), idListHandler, baselineVersionId, matrix.getId())
                );

                // sanity check
                matrix.setExpectedScoresCount(
                    (baseline.bandsCount.get(SymphonyCategory.ECOSYSTEM) - matrix.getMissingEcoBands().size()) *
                    (baseline.bandsCount.get(SymphonyCategory.PRESSURE) - matrix.getMissingPressureBands().size()));
                matrix.setActualScoresCount(query(combinationsQuery(schema), longHandler, matrix.getId()));
            }

            return baseline;
        } catch (SQLException sqlException) {
            throw new Exception("Error retrieving baseline data. Aborting");
        }
    }


    /**
     * Rows in reliabilitypartition reference meta_bands with no ON DELETE action, so
     * clearing band data for a baseline that has them fails part-way through.
     */
    public int countReliabilityPartitionRows(int bvId) throws SQLException {
        Long n = query(String.format(
            "SELECT count(*) FROM %1$s.reliabilitypartition rp "
                + "JOIN %1$s.meta_bands mb ON mb.metaband_id = rp.rp_metaband_id "
                + "WHERE mb.metaband_bver_id = ?", schema), longHandler, bvId);
        return n == null ? 0 : n.intValue();
    }

    /**
     * Owners of user-created sensitivity matrices on this baseline. Clearing band data
     * cascades through sensitivity.sens_*_band_fk and empties their matrices.
     */
    public List<String> userDefinedMatrixOwners(int bvId) throws SQLException {
        return query(String.format(
            "SELECT DISTINCT sensm_owner FROM %s.sensitivitymatrix "
                + "WHERE sensm_bver_id = ? AND sensm_owner IS NOT NULL ORDER BY 1", schema),
                new ColumnListHandler<>(), bvId);
    }

    /**
     * Calculation areas coupled to the given baseline version, by either route: the area's own
     * default matrix (calculationarea.carea_default_sensm_id, which is NOT NULL) or a secondary
     * calcareasensmatrix link. Both routes count, so an area whose default matrix belongs to
     * another baseline version is in scope as soon as it links to a matrix on this one. A
     * replace is meant to leave nothing behind that referenced what it removes.
     * <p>
     * The ids are collected up front and the deletes then issued in an explicit order, which
     * keeps the clear agnostic about the ON DELETE actions the schema happens to declare.
     */
    public static String coupledCalculationAreasQuery(String schema) {
        return String.format(
            "WITH scope AS (SELECT sensm_id FROM %1$s.sensitivitymatrix WHERE sensm_bver_id = ?) "
                + "SELECT ca.carea_id FROM %1$s.calculationarea ca "
                + "WHERE ca.carea_default_sensm_id IN (SELECT sensm_id FROM scope) "
                + "OR EXISTS (SELECT 1 FROM %1$s.calcareasensmatrix cm "
                + "WHERE cm.casen_carea_id = ca.carea_id "
                + "AND cm.casen_sensm_id IN (SELECT sensm_id FROM scope))", schema);
    }

    private int count(String query, int bvId) throws SQLException {
        Long n = query(query, longHandler, bvId);
        return n == null ? 0 : n.intValue();
    }

    public int countMetaBands(int bvId) throws SQLException {
        return count(String.format(
            "SELECT count(*) FROM %s.meta_bands WHERE metaband_bver_id = ?", schema), bvId);
    }

    public int countMetaValues(int bvId) throws SQLException {
        return count(String.format(
            "SELECT count(*) FROM %1$s.meta_values mv JOIN %1$s.meta_bands mb "
                + "ON mb.metaband_id = mv.metaval_band_id WHERE mb.metaband_bver_id = ?", schema), bvId);
    }

    public int countSensitivityMatrices(int bvId) throws SQLException {
        return count(String.format(
            "SELECT count(*) FROM %s.sensitivitymatrix WHERE sensm_bver_id = ?", schema), bvId);
    }

    public int countCoupledCalculationAreas(int bvId) throws SQLException {
        return count(String.format(
            "SELECT count(*) FROM (%s) coupled", coupledCalculationAreasQuery(schema)), bvId);
    }

    /**
     * Of the coupled areas, the ones another baseline version owns: they reach this baseline
     * version only through a calcareasensmatrix link, yet a replace deletes them all the same.
     * Counted apart from the rest so the confirmation prompt can say so. An operator can
     * predict losing this baseline version's own areas from the option name; losing another
     * baseline version's areas is the part worth stating outright.
     */
    public int countForeignCalculationAreas(int bvId) throws SQLException {
        return count(String.format(
            "WITH scope AS (SELECT sensm_id FROM %1$s.sensitivitymatrix WHERE sensm_bver_id = ?) "
                + "SELECT count(*) FROM %1$s.calculationarea ca "
                + "WHERE ca.carea_default_sensm_id NOT IN (SELECT sensm_id FROM scope) "
                + "AND EXISTS (SELECT 1 FROM %1$s.calcareasensmatrix cm "
                + "WHERE cm.casen_carea_id = ca.carea_id "
                + "AND cm.casen_sensm_id IN (SELECT sensm_id FROM scope))", schema), bvId);
    }

    public int countCalculationAreaPolygons(int bvId) throws SQLException {
        return count(String.format(
            "SELECT count(*) FROM (%2$s) coupled "
                + "JOIN %1$s.capolygon cap ON cap.cap_carea_id = coupled.carea_id",
            schema, coupledCalculationAreasQuery(schema)), bvId);
    }

    public int countSensitivityScores(int bvId) throws SQLException {
        return count(String.format(
            "SELECT count(*) FROM %1$s.sensitivity s JOIN %1$s.sensitivitymatrix m "
                + "ON m.sensm_id = s.sens_sensm_id WHERE m.sensm_bver_id = ?", schema), bvId);
    }

    /**
     * Counts everything a 'replace' of the given width will delete on this baseline version.
     * Counted before the import runs, so the operator is told what is at stake rather than
     * shown the wreckage afterwards.
     */
    public ReplacementImpact assessReplacement(int bvId, ClearScope scope) throws SQLException {
        boolean clearsBands = scope == ClearScope.BAND_METADATA;
        boolean clearsMatrices = scope != ClearScope.CALCULATION_AREAS;

        return new ReplacementImpact(
            clearsBands ? countMetaBands(bvId) : 0,
            clearsBands ? countMetaValues(bvId) : 0,
            clearsMatrices ? countSensitivityMatrices(bvId) : 0,
            clearsMatrices ? userDefinedMatrixOwners(bvId) : List.of(),
            countCoupledCalculationAreas(bvId),
            countForeignCalculationAreas(bvId),
            countCalculationAreaPolygons(bvId),
            clearsBands ? countReliabilityPartitionRows(bvId) : 0);
    }

    protected void clearBandData(int bvId) throws SQLException {
        Connection conn = getConnection();
        for (SymphonyCategory cat : SymphonyCategory.values()) {
            qr.update(conn, MetaValue.deleteQuery(schema, bvId, cat));
            qr.update(conn, SymphonyBand.deleteQuery(schema, bvId, cat));
        }
    }

    @FunctionalInterface
    public interface TransactionalWork {
        void run(Connection conn) throws SQLException, ParseException;
    }

    /**
     * Runs the given work as one transaction on the shared connection, restoring the previous
     * auto-commit setting afterwards.
     * <p>
     * The ordering here is load-bearing. setAutoCommit(true) commits an open transaction per
     * the JDBC contract, so anything that escapes the catch below (an Error, or a checked
     * exception a later edit introduces) would otherwise commit exactly the half-applied state
     * this wrapper exists to prevent. The transaction is therefore always ended explicitly
     * before the connection is restored, and {@code transactionEnded} is only set to true once
     * an end (commit or rollback) has actually succeeded. If the transaction still could not be
     * ended by the time the finally block runs, restoring auto-commit is exactly the commit this
     * method exists to prevent, so the connection is closed instead and discarded: a future
     * caller gets a fresh connection from {@link #getConnection()} rather than one that might
     * still be holding the half-applied state open.
     *
     * @param description names the operation in the warning printed if a rollback itself fails
     */
    protected void inTransaction(String description, TransactionalWork work)
            throws SQLException, ParseException {
        Connection conn = getConnection();

        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        boolean transactionEnded = false;

        try {
            work.run(conn);
            conn.commit();
            transactionEnded = true;
        } catch (SQLException | ParseException | RuntimeException e) {
            // Rolled back here rather than only in the finally, so that a failure to roll back
            // is attached to the original exception instead of replacing it
            try {
                conn.rollback();
                transactionEnded = true;
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            if (!transactionEnded) {
                try {
                    conn.rollback();
                    transactionEnded = true;
                } catch (SQLException rollbackFailure) {
                    System.err.println("Warning: could not roll back the aborted " + description
                        + ": " + rollbackFailure.getMessage());
                }
            }

            if (transactionEnded) {
                // Never let a failure to restore the connection mask the real outcome
                try {
                    conn.setAutoCommit(previousAutoCommit);
                } catch (SQLException restoreFailure) {
                    System.err.println(
                        "Warning: could not restore the connection's auto-commit setting: "
                            + restoreFailure.getMessage());
                }
            } else {
                // The transaction could not be ended by any means available. Restoring
                // auto-commit on this connection would, per the JDBC contract, commit whatever
                // half-applied state is still open on it, which is precisely what this wrapper
                // exists to prevent. Close the connection instead, so it is discarded rather
                // than reused to commit: getConnection() opens a replacement on next use.
                System.err.println("Warning: could not end the transaction for the aborted "
                    + description + "; closing the connection rather than risk committing it.");
                try {
                    conn.close();
                } catch (SQLException closeFailure) {
                    System.err.println("Warning: could not close the connection: "
                        + closeFailure.getMessage());
                }
            }
        }
    }

    /**
     * Empties the coupled data of one baseline version, to the requested width, in the only
     * order the foreign keys permit: reliability partitions before bands, area couplings and
     * polygons before areas, areas before matrices, matrices before bands.
     * <p>
     * Must run inside a transaction. Several of these deletes can fail part way through the
     * sequence, and a half-cleared baseline is worse than one that was never touched.
     */
    protected void clearCoupledData(int bvId, ClearScope scope) throws SQLException {
        if (getConnection().getAutoCommit()) {
            throw new IllegalStateException(
                "clearCoupledData must run inside a transaction: its deletes can fail part way "
                    + "through, and a half-cleared baseline version is worse than an untouched one");
        }

        if (scope == ClearScope.BAND_METADATA) {
            clearReliabilityPartitions(bvId);
        }

        clearCalculationAreas(bvId);

        if (scope != ClearScope.CALCULATION_AREAS) {
            clearSensitivityMatrices(bvId);
        }

        if (scope == ClearScope.BAND_METADATA) {
            clearBandData(bvId);
        }
    }

    public void updateMetadata(MetadataBase metadata, boolean clear) throws SQLException, ParseException {
        int blvId = metadata.settings.baselineVersion.getId();

        if (!metadata.confirmImport()) {
            throw new ParseException("Metadata import aborted interactively.");
        }

        // Clearing band data cascades into sensitivity scores and can fail part way through,
        // so the clear and the re-insert must succeed or fail as one unit.
        inTransaction("metadata import", conn -> {
            if (clear) {
                clearCoupledData(blvId, ClearScope.BAND_METADATA);
            }

            for (SymphonyCategory cat : SymphonyCategory.values()) {
                for (SymphonyBand band : metadata.bands.get(cat)) {
                    Integer bandId = qr.query(conn,
                        SymphonyBand.preBandExists(schema), idHandler,
                        blvId,
                        cat.getDbVal(),
                        band.getBandNumber());

                    if (bandId == null) {
                        bandId = qr.insert(conn, SymphonyBand.preBandInsert(schema), idHandler,
                            blvId, cat.getDbVal(), band.getBandNumber(), band.isDefaultSelected());
                        if (bandId == null) {
                            throw new SQLException("");
                        }
                    }

                    Map<String, String> metaMap = band.getMeta().get(metadata.settings.language);
                    for (Map.Entry<String, String> mv : metaMap.entrySet()) {
                        qr.insert(conn, SymphonyBand.preMetaInsert(schema), idHandler,
                            bandId, metadata.settings.language, mv.getKey(), mv.getValue());
                    }
                }
            }
        });

        System.out.println("Metadata import finished.");
    }

    public void updateMatrix(MatrixBase matrix, boolean clear) throws SQLException, ParseException {
        if (!matrix.confirmImport()) {
            throw new ParseException("Matrix import aborted interactively.");
        }

        int bvId = matrix.settings.baselineVersion.getId();

        // The clear deletes calculation areas before the matrices they reference, and the
        // insert that follows must not be separable from it: carea_default_sensm_id restricts
        // deletion of a referenced matrix, so an unguarded clear commits the sensitivity rows
        // and then fails, leaving a matrix with no scores.
        inTransaction("matrix import", conn -> {
            if (clear) {
                clearCoupledData(bvId, ClearScope.MATRICES);
            }

            int mxId = qr.insert(conn, MatrixBase.insertSensMatrix(schema), idHandler,
                matrix.settings.getMatrixName(),
                matrix.settings.baselineVersion.getId());

            String[] valuesToInsert = matrix.getSensitivities().stream()
                .map(s -> s.insertRowValue(mxId)).toArray(String[]::new);

            qr.execute(conn, String.format("%s %s",
                Sensitivity.insertRowColumns(schema),
                String.join(",", valuesToInsert)));
        });
    }

    public void updateNationalAreas(NationalAreaRowInsert[] areaInserts) throws SQLException {
        String[] areaCountryISOs = Arrays.stream(areaInserts)
            .map(NationalAreaRowInsert::getCountryISO).toArray(String[]::new);

        // Each entry is a DELETE followed by an INSERT, and NationalAreaRowInsert.getPolygon()
        // reads its file lazily, right here, so a bad file (missing, unreadable, changed since
        // an earlier '-f' validation pass) can fail after an earlier entry's delete has already
        // run. getPolygon() throws a RuntimeException wrapping an IO failure, which the wrapper
        // also rolls back on.
        try {
            inTransaction("national areas import", conn -> {
                for (NationalAreaRowInsert areaInsert : areaInserts) {
                    qr.update(conn, NationalAreaRowInsert.cleanQuery(schema),
                        areaInsert.getCountryISO(), areaInsert.getNationalAreaType());
                    qr.update(conn, NationalAreaRowInsert.insertQuery(schema),
                        areaInsert.getCountryISO(), areaInsert.getPolygon(),
                        areaInsert.getNationalAreaType());
                }

                // sanitize table, once per distinct country
                for (String iso : Arrays.stream(areaCountryISOs).distinct().toList()) {
                    qr.update(conn,
                        String.format("DELETE FROM %s.nationalarea "
                            + "WHERE narea_type = ? AND narea_countryiso3 = ?", schema),
                        NationalAreaRowInsert.TYPE_TYPES, iso);

                    String types = jsonStringArray(
                        Arrays.stream(qr.query(conn,
                            areaTypesExclusiveQuery(schema),
                            new ArrayHandler(), iso, TYPE_BOUNDARY))
                                .map(Object::toString).toArray(String[]::new));

                    qr.update(conn,
                        String.format("INSERT INTO %s.nationalarea "
                            + "(narea_countryiso3, narea_type, narea_types) VALUES (?, ?, ?)", schema),
                        iso, NationalAreaRowInsert.TYPE_TYPES, types);
                }
            });
        } catch (ParseException e) {
            // Nothing in the block above throws ParseException; the wrapper's signature carries
            // it for the import paths that do.
            throw new SQLException(e.getMessage(), e);
        }

        System.out.println("National areas import finished.");
    }

    public int insertBaselineVersion(BaselineVersion baselineVersion) throws SQLException {
        Connection conn = getConnection();
        return qr.insert(conn, BaselineVersion.preBaselineVersionInsert(schema), idHandler,
            baselineVersion.getName(), baselineVersion.getTitle(),
            baselineVersion.getDescription(), baselineVersion.getValidFrom(), baselineVersion.getEcoFilePath(), baselineVersion.getPressureFilePath(), baselineVersion.getLocale());
    }

    static String titlesQuery(String schema, int bverId) {
        return String.format(
            "SELECT mb.metaband_id FROM %1$s.meta_bands mb " +
                "JOIN %1$s.baselineversion bl ON " +
                    "mb.metaband_bver_id = bl.bver_id " +
                "JOIN %1$s.meta_values m ON " +
                    "m.metaval_band_id = mb.metaband_id " +
                    "AND m.metaval_language = ? " +
                    "AND m.metaval_field = 'title' " +
            "WHERE " +
                "mb.metaband_bver_id = %2$d " +
                "AND mb.metaband_category = ? " +
                "AND m.metaval_value = ?", schema, bverId);
    }

    public static String delimitedIds(int[] intArray) {
        return intArray.length > 0 ? StringUtils.join(ArrayUtils.toObject(intArray), ",") : "0";
    }

    public List<DbMatrix> getMatricesById(int[] matrixIds) throws SQLException {
        return query(matricesQuery(schema, matrixIds), DbMatrix.handler);
    }

    public List<DbMatrix> getMatricesForBaselineVersion(int bverId) throws SQLException {
        return query(baselineMatricesQuery(schema), DbMatrix.handler, bverId);
    }

    public Map<String, Integer> getMatrixMap(int bverId) throws SQLException {
        List<DbMatrix> matrices = getMatricesForBaselineVersion(bverId);
        Map<java.lang.String, java.lang.Integer> matrixMap = HashMap.newHashMap(matrices.size());

        for (DbMatrix matrix : matrices) {
            matrixMap.put(matrix.getName(), matrix.getId());
        }

        return matrixMap;
    }

    public List<DbCalculationArea> getCalcAreasForMatrixIds(int[] matrixIds) throws SQLException {
        return query(defaultCalcAreasForMatrices(schema, matrixIds), DbCalculationArea.handler);
    }

    public List<Integer> getMatrixIdsForBaselineVersion(int bverId) throws SQLException {
        return query(baselineMatricesQuery(this.schema), idListHandler, bverId);
    }

    public List<DbNationalArea> getAllNationalAreas() throws SQLException {
        return query(allAreasCheapQuery(schema), DbNationalArea.handler);
    }

    public static String matricesQuery(String schema, int[] matrixIds) {
        return String.format(
            "SELECT sm.sensm_id, sm.sensm_name FROM %s.sensitivitymatrix sm WHERE sm.sensm_id IN (%2$s)",
                schema, delimitedIds(matrixIds));
    }

    public static String baselineMatricesQuery(String schema) {
        return String.format(
            "SELECT sm.sensm_id, sm.sensm_name FROM %s.sensitivitymatrix sm WHERE sm.sensm_bver_id = ?", schema);
    }

    public static String defaultCalcAreasForMatrices(String schema, int[] matrixIds) {
        return String.format(
            "SELECT ca.carea_id, ca.carea_name, ca.carea_default, ca.carea_default_sensm_id FROM %1$s.calculationarea ca " +
            "JOIN %1$s.sensitivitymatrix sm ON ca.carea_default_sensm_id = sm.sensm_id " +
            "WHERE ca.carea_default = true AND sm.sensm_id IN (%2$s)",
                schema, delimitedIds(matrixIds));
    }

    public Integer getBandIdByCategoryTitleAndBaseline(int bverId, SymphonyCategory cat, String title) throws SQLException {
        return qr.query(getConnection(), titlesQuery(schema, bverId), idHandler, cat.getDbVal(), title);
    }

    public static String jsonStringArray(String[] array) {
        return Arrays.stream(array)
            .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
            .collect(Collectors.joining(",", "[", "]"));
    }

    public <T> T query(String query, ResultSetHandler<T> handler, Object ...args) throws SQLException {
        return qr.query(getConnection(), query, handler, args);
    }

    public void importCalculationAreas(CalcAreaProcedure.AreaMatrixTuple[] calcAreaMatrixTuples,
                                       int bvId, boolean clear) throws SQLException, ParseException {
        // The loop below throws when an area names a matrix that does not exist on this
        // baseline, which is reachable with entirely valid input, so the clear that precedes
        // it must be undone rather than left committed.
        inTransaction("calculation area import", conn -> {
            if (clear) {
                clearCoupledData(bvId, ClearScope.CALCULATION_AREAS);
            }

            for (CalcAreaProcedure.AreaMatrixTuple camx : calcAreaMatrixTuples) {
                CalculationArea ca = camx.area();
                Integer matrixId = qr.query(conn,
                    String.format("SELECT sensm_id FROM %s.sensitivitymatrix "
                        + "WHERE sensm_name = ? AND sensm_bver_id = ?", schema),
                    idHandler, ca.getMatrixName(), bvId);
                if (matrixId == null) {
                    throw new ParseException(String.format(
                        "No sensitivity matrix named '%s' exists on baseline version %d. "
                            + "Import the matrix before the calculation areas that reference it.",
                        ca.getMatrixName(), bvId));
                }

                Integer lastPolyId = qr.insert(conn,
                    CalculationArea.calcAreaInsert(schema, json.toString(ca.getPolygon())), idHandler,
                        ca.getAreaName(), matrixId, ca.isDefault(), ca.getAreaType());

                // 'extra' 'round trip' to get the area ID, seems unavoidable
                Integer areaId = qr.query(conn,
                    String.format("SELECT cap_carea_id FROM %s.capolygon WHERE cap_id = ?", schema),
                    idHandler, lastPolyId);
                if (!camx.matrixIds().isEmpty()) {
                    qr.insert(conn, CalculationArea.additionalMatrixCouplingInsert(
                        schema, areaId, camx.matrixIds()), idHandler);
                }
            }
        });
    }

    private void clearReliabilityPartitions(int bvId) throws SQLException {
        qr.update(getConnection(), String.format(
            "DELETE FROM %1$s.reliabilitypartition rp "
            + "USING %1$s.meta_bands mb WHERE mb.metaband_id = rp.rp_metaband_id "
            + "AND mb.metaband_bver_id = ?", schema), bvId);
    }

    private void clearSensitivityMatrices(int bvId) throws SQLException {
        Connection conn = getConnection();

        // For robustness, rows that should be removed implicitly by cascading from the
        // removal of the corresponding meta band entries are removed explicitly upfront
        // We're neither relying on the expected FK relationship sensitivity -> parent
        // matrix (second query should be sufficient in a correctly configured ds)
        qr.update(conn, String.format(
            "DELETE FROM %1$s.sensitivity s USING %1$s.sensitivitymatrix m " +
            "WHERE s.sens_sensm_id = m.sensm_id AND m.sensm_bver_id = ?", schema), bvId);

        qr.update(conn, String.format(
            "DELETE FROM %1$s.sensitivitymatrix m WHERE m.sensm_bver_id = ?", schema), bvId);
    }

    /**
     * Removes every calculation area coupled to this baseline version, with its polygons and
     * all of its matrix couplings. Coupling is either route, default matrix or secondary link,
     * so an area owned by another baseline version goes too once it references a matrix here.
     * <p>
     * The link rows are cleared by area (casen_carea_id) rather than by matrix
     * (casen_sensm_id). Every area holding a link to a matrix in scope is itself in the delete
     * set, so a matrix-keyed delete would remove nothing this one misses; and clearing by area
     * also removes that area's links to matrices on other baseline versions, which a
     * matrix-keyed delete would leave behind to block the area delete through casen_carea_fk,
     * which has no ON DELETE action.
     */
    private void clearCalculationAreas(int bvId) throws SQLException {
        Connection conn = getConnection();

        List<Integer> coupledCalcAreas =
            query(coupledCalculationAreasQuery(schema), idListHandler, bvId);

        if (coupledCalcAreas.isEmpty()) {
            return;
        }

        String areaIds = delimitedIds(
            coupledCalcAreas.stream().mapToInt(Integer::intValue).toArray());

        qr.update(conn, String.format(
            "DELETE FROM %1$s.calcareasensmatrix WHERE casen_carea_id IN (%2$s)", schema, areaIds));

        qr.update(conn, String.format(
            "DELETE FROM %1$s.capolygon WHERE cap_carea_id IN (%2$s)", schema, areaIds));

        qr.update(conn, String.format(
            "DELETE FROM %1$s.calculationarea WHERE carea_id IN (%2$s)", schema, areaIds));
    }
}
