package se.havochvatten.symphony_setup.setup.model;

import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import java.util.List;
import java.util.Map;

public class MetaValue {
    public static String selectQuery(String schema, int bvId, SymphonyCategory category) {
        return String.format(
            "SELECT mv.metaval_band_id, mv.metaval_field, mv.metaval_value, mv.metaval_language " +
            "FROM %1$s.meta_values mv " +
            "JOIN %1$s.meta_bands mb ON mv.metaval_band_id = mb.metaband_id " +
            "WHERE mb.metaband_bver_id = %2$d AND mb.metaband_category = '%3$s' " +
                "ORDER BY mv.metaval_band_id",
            schema, bvId, category.getDbVal()
        );
    }

    // Explicitly not relying on cascade delete via fk metaval_band_id to parent band
    public static String deleteQuery(String schema, int bvId, SymphonyCategory category) {
        return String.format("DELETE FROM %s.meta_values mv WHERE mv.metaval_band_id IN " +
            "(SELECT metaband_id FROM symphony.meta_bands WHERE metaband_bver_id = %d AND UPPER(metaband_category) = '%s')",
            schema, bvId, category.name());
    }

    public static String getAllValuesByFieldQuery(String schema, int bvId, SymphonyCategory type, String language, String field) {
        return String.format("SELECT mv.value FROM %1$s.meta_values mv " +
            "JOIN %1$s.meta_bands mb ON mv.metaval_band_id = mb.metaband_id " +
            "WHERE mb.metaband_bver_id = %2$d AND " +
            "mb.metaband_category = '%3$s' AND " +
            "mv.metaval_language = '%4$s' AND " +
            "mv.meta_field = '%5$s'",
            schema, bvId, type.getDbVal(), language, field);
    }

    public static ResultSetHandler<List<MetaValue>> handler;
    static {
        RowProcessor rp = new BasicRowProcessor(
            new BeanProcessor(
                Map.of("metaval_band_id", "bandId",
                       "metaval_field", "field",
                       "metaval_value", "value",
                       "metaval_language", "language"))
        );

        handler = new BeanListHandler<>(MetaValue.class, rp);
    }

    private int bandId;
    private String field;
    private String value;
    private String language;

    public MetaValue() {}

    public MetaValue (String field, String value, String language) {
        this.bandId = -1;
        this.field = field;
        this.value = value;
        this.language = language;
    }

    public int getBandId() {
        return bandId;
    }

    public void setBandId(int bandId) {
        this.bandId = bandId;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
