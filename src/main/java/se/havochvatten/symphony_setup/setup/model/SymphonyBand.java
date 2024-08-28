package se.havochvatten.symphony_setup.setup.model;

import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SymphonyBand {

    private int id;
    private int bandNumber;
    private Boolean defaultSelected = null;
    private String defaultLanguage = "en";

    private final Map<String, Map<String, String>> meta = new HashMap<>();

    public static String selectQuery(String schema, int bvId, SymphonyCategory type) {
        return String.format("SELECT sb.metaband_id, sb.metaband_number, sb.metaband_default_selected " +
               "FROM %s.meta_bands sb WHERE sb.metaband_bver_id = %d AND UPPER(metaband_category) = '%s' " +
               "ORDER BY sb.metaband_number", schema, bvId, type.name());
    }

    // Note: we're not relying on foreign key cascading delete:
    // Values will/should be cleared before this query is run.
    public static String deleteQuery(String schema, int bvId, SymphonyCategory type) {
        return String.format("DELETE from %s.meta_bands sb WHERE sb.metaband_bver_id = %d " +
                "AND UPPER(metaband_category) = '%s'", schema, bvId, type.name());
    }

    public static ResultSetHandler<List<SymphonyBand>> handler;
    static {
        RowProcessor rp = new BasicRowProcessor(
            new BeanProcessor(Map.of(
                "metaband_id", "id",
                "metaband_number", "bandNumber",
                "metaband_default_selected", "defaultSelected"
                )
            )
        );

        handler = new BeanListHandler<>(SymphonyBand.class, rp);
    }

    public static String preBandInsert(String schema) {
        return String.format(
            "INSERT INTO %s.meta_bands (metaband_bver_id, metaband_category, metaband_number, " +
                "metaband_default_selected)" +
                "VALUES (?, ?, ?, ?)", schema);
    }

    public static String preBandExists(String schema) {
        return String.format(
            "SELECT metaband_id FROM %s.meta_bands " +
                "WHERE metaband_bver_id = ? " +
                "AND metaband_category = ? " +
                "AND metaband_number = ?", schema);
    }

    public static String preMetaInsert(String schema) {
        return String.format(
            "INSERT INTO %s.meta_values(metaval_band_id, metaval_language, " +
                "metaval_field, metaval_value) VALUES " +
                "(?, ?, ?, ?) " +
            "ON CONFLICT (metaval_band_id, metaval_language, metaval_field) " +
            "DO UPDATE SET metaval_field = EXCLUDED.metaval_field", schema);
    }

    public void setMetaValue(String language, MetaValue metaValue) {
        meta.putIfAbsent(language, new HashMap<>());

        meta.get(language)
            .put(metaValue.getField(), metaValue.getValue());
    }

    public SymphonyBand(){}

    public SymphonyBand(String language, int bandNumber, Boolean defaultSelected) {
        this.defaultLanguage = language;
        this.bandNumber = bandNumber;
        this.defaultSelected = defaultSelected;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBandNumber() {
        return bandNumber;
    }

    public void setBandNumber(int bandNumber) {
        this.bandNumber = bandNumber;
    }

    public boolean isDefaultSelected() {
        return defaultSelected != null && defaultSelected;
    }

    public void setDefaultSelected(boolean defaultSelected) {
        this.defaultSelected = defaultSelected;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    public Map<String, Map<String, String>> getMeta() {
        return meta;
    }

    public String getTitle() {
        return meta.get(this.defaultLanguage).get("title");
    }
}
