package se.havochvatten.symphonyconfig.setup.model;

import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.RowProcessor;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import java.util.List;
import java.util.Map;

public class DbCalculationArea {
    private int id;
    private String name;
    private boolean defaultArea;
    private Integer defaultSensitivityMatrixId;

    public static final ResultSetHandler<List<DbCalculationArea>> handler;

    static {
        RowProcessor rp = new BasicRowProcessor(
            new BeanProcessor(
                Map.of("carea_id", "id",
                    "carea_name", "name",
                    "carea_default", "defaultArea",
                    "carea_default_sensm_id", "defaultSensitivityMatrixId"
                )
            )
        );

        handler = new BeanListHandler<>(DbCalculationArea.class, rp);
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

    public boolean isDefaultArea() {
        return defaultArea;
    }

    public void setDefaultArea(boolean defaultArea) {
        this.defaultArea = defaultArea;
    }

    public Integer getDefaultSensitivityMatrixId() {
        return defaultSensitivityMatrixId;
    }

    public void setDefaultSensitivityMatrixId(Integer defaultSensitivityMatrixId) {
        this.defaultSensitivityMatrixId = defaultSensitivityMatrixId;
    }
}
