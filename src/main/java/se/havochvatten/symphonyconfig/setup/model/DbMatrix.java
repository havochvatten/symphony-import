package se.havochvatten.symphonyconfig.setup.model;

import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import java.util.List;
import java.util.Map;

public class DbMatrix {
    private int id;
    private String name;
    List<Integer> missingEcoBands;
    List<Integer> missingPressureBands;

    private long expectedScoresCount;
    private long actualScoresCount;

    public static final ResultSetHandler<List<DbMatrix>> handler;

    static {
        RowProcessor rp = new BasicRowProcessor(
            new BeanProcessor(
                Map.of("sensm_id", "id",
                        "sensm_name", "name"
                )
            )
        );

        handler = new BeanListHandler<>(DbMatrix.class, rp);
    }

    public static String combinationsQuery(String schema) {
        return String.format(
            "SELECT COALESCE((SELECT COUNT(DISTINCT (sens_pres_band_id, sens_eco_band_id)) " +
                "FROM %s.sensitivity WHERE sens_sensm_id = ? GROUP BY sens_sensm_id), 0) AS count", schema);
    }

    public static String missingBandNumbersQuery(String schema, SymphonyCategory category) {
        String sensitivityColumn = category == SymphonyCategory.ECOSYSTEM ? "sens_eco_band_id" : "sens_pres_band_id";

        return String.format("SELECT metaband_number FROM %s.meta_bands " +
            "WHERE metaband_bver_id = ? AND metaband_category = '%s' " +
            "AND metaband_id NOT IN (SELECT DISTINCT %s FROM symphony.sensitivity WHERE sens_sensm_id = ?)",
                schema, category.getDbVal(), sensitivityColumn);
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

    public List<Integer> getMissingEcoBands() {
        return missingEcoBands;
    }

    public void setMissingEcoBands(List<Integer> missingEcoBands) {
        this.missingEcoBands = missingEcoBands;
    }

    public List<Integer> getMissingPressureBands() {
        return missingPressureBands;
    }

    public void setMissingPressureBands(List<Integer> missingPressureBands) {
        this.missingPressureBands = missingPressureBands;
    }

    public long getExpectedScoresCount() {
        return expectedScoresCount;
    }

    public void setExpectedScoresCount(long expectedScoresCount) {
        this.expectedScoresCount = expectedScoresCount;
    }

    public long getActualScoresCount() {
        return actualScoresCount;
    }

    public void setActualScoresCount(long actualScoresCount) {
        this.actualScoresCount = actualScoresCount;
    }
}
