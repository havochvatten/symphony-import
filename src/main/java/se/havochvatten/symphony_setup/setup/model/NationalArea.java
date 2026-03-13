package se.havochvatten.symphony_setup.setup.model;

import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import java.util.List;
import java.util.Map;

public class NationalArea {
    protected int id;
    protected String type;
    protected String areasJson;
    protected String countryCode;

    public static final ResultSetHandler<List<NationalArea>> handler =
        new BeanListHandler<>(NationalArea.class,
            new BasicRowProcessor(
                new BeanProcessor(
                    Map.of("narea_id", "id",
                        "narea_type", "type",
                        "narea_areas", "areasJson",
                        "narea_countryiso3", "countryCode"))
            )
        );

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAreasJson() {
        return areasJson;
    }

    public void setAreasJson(String areasJson) {
        this.areasJson = areasJson;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public static String areaTypesExclusiveQuery(String schema) {
        return String.format("SELECT narea_type FROM %s.nationalarea WHERE narea_countryiso3 = ? AND NOT narea_type = ?", schema);
    }
}
