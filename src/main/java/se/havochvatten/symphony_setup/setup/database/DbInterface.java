package se.havochvatten.symphony_setup.setup.database;

import org.apache.commons.cli.ParseException;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import se.havochvatten.symphony_setup.setup.model.*;
import se.havochvatten.symphony_setup.setup.process.MetadataBase;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
    protected static final ScalarHandler<Integer> idHandler = new ScalarHandler<>();

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
    public BaselineVersion getBaselineVersion(Integer baselineVersionId) throws SQLException {

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

    public int[] getAvailableBaselineVersionIds() throws SQLException {
        Connection conn = getConnection();

        ColumnListHandler<Integer> versionIdsHandler = new ColumnListHandler<>(1);
        return qr.query(conn, BaselineVersion.selectAvailableVersions(this.schema), versionIdsHandler)
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

    protected void clearBandData(int bvId) throws SQLException {
        Connection conn = getConnection();
        for (SymphonyCategory cat : SymphonyCategory.values()) {
            qr.update(getConnection(), MetaValue.deleteQuery(schema, bvId, cat));
            qr.update(getConnection(), SymphonyBand.deleteQuery(schema, bvId, cat));
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

            // TODO: meddela status
            System.out.println("Metadata import finished.");
        } else {
            throw new ParseException("Metadata import aborted interactively.");
        }
    }
}
