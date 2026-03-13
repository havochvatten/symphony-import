package se.havochvatten.symphony_setup.setup.model;

import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import se.havochvatten.symphony_setup.setup.database.BaselineBeanProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class BaselineVersion {

    private int id;
    private String name;
    private String description;
    private LocalDate validFrom;
    private String ecoFilePath;
    private String pressureFilePath;
    private String locale;

    public BaselineVersion() {}

    public BaselineVersion(String name, String description, LocalDate validFrom, String ecoFilename, String pressureFilename, String locale) {
        this.id = -1;
        this.name = name;
        this.description = description;
        this.validFrom = validFrom;
        this.ecoFilePath = ecoFilename;
        this.pressureFilePath = pressureFilename;
        this.locale = locale;
    }

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
            new BaselineBeanProcessor(
                Map.of("bver_id", "id",
                       "bver_name", "name",
                       "bver_desc", "description",
                       "bver_validfrom", "validFrom",
                       "bver_ecofilepath", "ecoFilePath",
                       "bver_presfilepath", "pressureFilePath",
                       "bver_locale", "locale"
                    )
            )
        );

        handler = new BeanListHandler<>(BaselineVersion.class, rp);
    }

    public static String selectAvailableVersions(String schema) {
        return String.format("SELECT bver_id FROM %s.baselineversion ORDER BY bver_id", schema);
    }

    public static String preBaselineVersionInsert(String schema) {
        return String.format(
            "INSERT INTO %s.baselineversion (bver_name, bver_desc, bver_validfrom, " +
                    "bver_ecofilepath, bver_presfilepath, bver_locale)" +
                    "VALUES (?, ?, ?, ?, ?, ?)", schema);
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

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public String getEcoFilePath() {
        return ecoFilePath;
    }

    public void setEcoFilePath(String ecoFilePath) {
        this.ecoFilePath = ecoFilePath;
    }

    public String getPressureFilePath() {
        return pressureFilePath;
    }

    public void setPressureFilePath(String pressureFilePath) {
        this.pressureFilePath = pressureFilePath;
    }

    public Map<SymphonyCategory, String> tiffFilePaths() {
        return Map.of(SymphonyCategory.ECOSYSTEM, ecoFilePath,
                      SymphonyCategory.PRESSURE, pressureFilePath);
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
