package se.havochvatten.symphony_setup.setup.model;

import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class BaselineVersion {

    private int id;
    private String name;
    private String description;
    private Date validFrom;
    private String ecoFilename;
    private String pressureFilename;
    private String locale;

    public BaselineVersion() {}

    public static String selectLatestQuery(String schema) {
        return String.format(
            "SELECT b.bver_id, b.bver_name, b.bver_desc, b.bver_validfrom, " +
                   "b.bver_ecofilepath, b.bver_presfilepath, b.bver_locale " +
            "FROM %1$s.baselineversion b " +
            "JOIN (SELECT max(bver_validfrom) maxv FROM %1$s.baselineversion) bm " +
            "ON b.bver_validfrom = bm.maxv", schema);
    }

    public static String selectSpecificQuery(String schema, int version) {
        return String.format("SELECT b.bver_id, b.bver_name, b.bver_desc, b.bver_validfrom, " +
                                    "b.bver_ecofilepath, b.bver_presfilepath, b.bver_locale " +
                                     "FROM %s.baselineversion b " +
                                        "WHERE b.bver_id = %d", schema, version);
    }

    public static final ResultSetHandler<List<BaselineVersion>> handler;
    static {
        RowProcessor rp = new BasicRowProcessor(
            new BeanProcessor(
                Map.of("bver_id", "id",
                       "bver_name", "name",
                       "bver_desc", "description",
                       "bver_validfrom", "validFrom",
                       "bver_ecofilepath", "ecoFilename",
                       "bver_presfilepath", "pressureFilename",
                       "bver_locale", "locale"
                    )
            )
        );

        handler = new BeanListHandler<>(BaselineVersion.class, rp);
    }

    public static String selectAvailableVersions(String schema) {
        return String.format("SELECT bver_id FROM %s.baselineversion ORDER BY bver_id", schema);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Date validFrom) {
        this.validFrom = validFrom;
    }

    public String getEcoFilename() {
        return ecoFilename;
    }

    public void setEcoFilename(String ecoFilename) {
        this.ecoFilename = ecoFilename;
    }

    public String getPressureFilename() {
        return pressureFilename;
    }

    public void setPressureFilename(String pressureFilename) {
        this.pressureFilename = pressureFilename;
    }

    public Map<SymphonyCategory, String> tiffFilePaths() {
        return Map.of(SymphonyCategory.ECOSYSTEM,  ecoFilename,
                      SymphonyCategory.PRESSURE,   pressureFilename);
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
