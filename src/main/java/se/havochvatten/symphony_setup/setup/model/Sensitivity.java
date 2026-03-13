package se.havochvatten.symphony_setup.setup.model;

import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Sensitivity {

    private int eBandId;
    private int pBandId;
    private double value;

    public static String selectQuery(String schema, int bvId, int sensMxId) {
        return String.format("SELECT sens_pres_band_id, sens_eco_band_id, sens_value " +
            "FROM %s.sensitivity s " +
            "JOIN meta_band mbe ON mbe.metaband_id = sens_eco_band_id " +
            "JOIN meta_band mbp ON mbp.metaband_id = sens_pres_band_id " +
            "WHERE mbe.metaband_bver_id = %2$d AND mbp.metaband_bver_id = %2$d " +
            "AND s.sens_sensm_id = %3$d"
            , schema, bvId, sensMxId);
    }

    public static String insertRowColumns(String schema) {
        return String.format("INSERT INTO %s.sensitivity " +
            "(sens_pres_band_id, sens_eco_band_id, sens_value, sens_sensm_id) VALUES ", schema);
    }

    public String insertRowValue(int sensMxId) {
        return String.format(Locale.US, "(%d, %d, %f, %d)", pBandId, eBandId, value, sensMxId);
    }

    public String pressureBandIdsQuery(String schema) {
        return String.format(
            "SELECT DISTINCT sens_pres_band_id FROM %s.sensitivity WHERE sens_sensm_id = ?", schema
        );
    }

    public String ecoBandIdsQuery(String schema) {
        return String.format(
            "SELECT DISTINCT sens_eco_band_id FROM %s.sensitivity WHERE sens_sensm_id = ?", schema
        );
    }

    public final static ResultSetHandler<List<Sensitivity>> handler;
    static {
        RowProcessor rp = new BasicRowProcessor(
            new BeanProcessor(Map.of(
                "sens_pres_band_id", "pBandId",
                "sens_eco_band_id", "eBandId",
                "sens_value", "value"
                )
            )
        );

        handler = new BeanListHandler<>(Sensitivity.class, rp);
    }

    public Sensitivity(int eBandId, int pBandId, double value) {
        this.eBandId = eBandId;
        this.pBandId = pBandId;
        this.value = value;
    }

    public int getEBandId() {
        return eBandId;
    }

    public void setEBandId(int eBandId) {
        this.eBandId = eBandId;
    }

    public int getPBandId() {
        return pBandId;
    }

    public void setPBandId(int pBandId) {
        this.pBandId = pBandId;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}
