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
        Integer bvId = qr.query(getConnection(),
            String.format("SELECT bver_id from %s.baselineversion WHERE bver_name = '%s'", this.schema, bvName),
            idHandler);
        return bvId;
    }

    public int[] getAvailableBaselineVersionIds() throws SQLException {
        Connection conn = getConnection();

        return qr.query(conn, BaselineVersion.selectAvailableVersions(this.schema), idListHandler)
                        .stream().mapToInt(Integer::valueOf).toArray();
    }

    public Set<Integer> getAvailableAreaTypes() throws SQLException {
        Connection conn = getConnection();

        return new HashSet<>(
            qr.query(conn, AreaType.getAvailableAreaTypeIdsQuery(this.schema), idListHandler)
        );
    }

    public int[] getAvailableCalculationAreaIds() throws SQLException {
        Connection conn = getConnection();

        return qr.query(conn, String.format("SELECT carea_id FROM %s.calculationarea", this.schema), idListHandler)
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


    protected void clearBandData(int bvId) throws SQLException {
        Connection conn = getConnection();
        for (SymphonyCategory cat : SymphonyCategory.values()) {
            qr.update(conn, MetaValue.deleteQuery(schema, bvId, cat));
            qr.update(conn, SymphonyBand.deleteQuery(schema, bvId, cat));
        }
    }

    public void updateMetadata(MetadataBase metadata) throws SQLException, ParseException {
        Connection conn = getConnection();
        int blvId = metadata.settings.baselineVersion.getId();
        Integer bandId;

        if (metadata.confirmImport()) {
            if(metadata.settings.clear) {
                clearBandData(blvId);
            }

            for (SymphonyCategory cat : SymphonyCategory.values()) {

                for (SymphonyBand band : metadata.bands.get(cat)) {
                    bandId = qr.query(conn,
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

            System.out.println("Metadata import finished.");
        } else {
            throw new ParseException("Metadata import aborted interactively.");
        }
    }

    public void updateMatrix(MatrixBase matrix) throws SQLException, ParseException {
        Connection conn = getConnection();

        if (matrix.confirmImport()) {
            int mxId = qr.insert(conn, MatrixBase.insertSensMatrix(schema), idHandler,
                matrix.settings.getMatrixName(),
                matrix.settings.baselineVersion.getId());

            String[] valuesToInsert = matrix.getSensitivities().stream()
                .map(s -> s.insertRowValue(mxId)).toArray(String[]::new);

            qr.execute(conn, String.format("%s %s",
                Sensitivity.insertRowColumns(schema),
                String.join(",", valuesToInsert)));


        } else {
            throw new ParseException("Matrix import aborted interactively.");
        }
    }

    public void updateNationalAreas(NationalAreaRowInsert[] areaInserts) throws SQLException {
        Connection conn = getConnection();

        String[] areaCountryISOs = Arrays.stream(areaInserts).map(NationalAreaRowInsert::getCountryISO).toArray(String[]::new);

        for  (NationalAreaRowInsert areaInsert : areaInserts) {
            this.qr.update(this.activeConnection, NationalAreaRowInsert.cleanQuery(schema), areaInsert.getCountryISO(), areaInsert.getNationalAreaType());
            this.qr.update(this.activeConnection, NationalAreaRowInsert.insertQuery(schema),
                areaInsert.getCountryISO(), areaInsert.getPolygon(), areaInsert.getNationalAreaType());
        }

        // sanitize table
        for (String iso :areaCountryISOs) {
            this.qr.update(conn,
                String.format("DELETE FROM %s.nationalarea WHERE narea_type = ?", schema),
                NationalAreaRowInsert.TYPE_TYPES);

            String types = jsonStringArray(
                Arrays.stream(this.qr.query(conn,
                    areaTypesExclusiveQuery(schema),
                    new ArrayHandler(), iso, TYPE_BOUNDARY))
                        .map(Object::toString).toArray(String[]::new));

            this.qr.update(conn,
                String.format("INSERT INTO %s.nationalarea (narea_countryiso3, narea_type, narea_types) VALUES (?, ?, ?)", schema),
                iso, NationalAreaRowInsert.TYPE_TYPES, types);
        }

        System.out.println("National areas import finished.");
    }

    public int insertBaselineVersion(BaselineVersion baselineVersion) throws SQLException {
        Connection conn = getConnection();
        return qr.insert(conn, BaselineVersion.preBaselineVersionInsert(schema), idHandler,
            baselineVersion.getName(),
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

    public void importCalculationAreas(CalcAreaProcedure.AreaMatrixTuple[] calcAreaMatrixTuples) throws SQLException, ParseException {
        for (CalcAreaProcedure.AreaMatrixTuple camx : calcAreaMatrixTuples) {
            CalculationArea ca = camx.area();
            Integer matrixId = this.query(
                String.format("SELECT sensm_id FROM %s.sensitivitymatrix WHERE sensm_name = ?", schema),
                idHandler, ca.getMatrixName());
            if (matrixId == null) {
                throw new ParseException(String.format("No sensitivity matrix with name %s found", ca.getMatrixName()));
            }

            Connection conn = getConnection();
            Integer lastPolyId = qr.insert(conn,
                CalculationArea.calcAreaInsert(schema, json.toString(ca.getPolygon())), idHandler,
                    ca.getAreaName(), matrixId, ca.isDefault(), ca.getAreaType());

            // 'extra' 'round trip' to get the area ID, seems unavoidable
            Integer areaId = this.query(
                String.format("SELECT cap_carea_id FROM %s.capolygon WHERE cap_id = ?", schema),
                idHandler, lastPolyId);
            if (!camx.matrixIds().isEmpty()) {
                qr.insert(conn, CalculationArea.additionalMatrixCouplingInsert(schema, areaId, camx.matrixIds()), idHandler);
            }
        }
    }
}
