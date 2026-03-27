package se.havochvatten.symphonyconfig.setup.model;

import org.apache.commons.dbutils.ResultSetHandler;
import org.locationtech.jts.geom.Geometry;

import java.sql.ResultSet;
import java.util.List;
import java.util.stream.Collectors;

public class CalculationArea {
    private final String areaName;
    private final String matrixName;
    private final Geometry polygon;
    private final boolean isDefault;
    private Integer areaType = null;

    public CalculationArea(String areaName, String matrixName, Geometry polygon, boolean isDefault) {
        this.areaName = areaName;
        this.matrixName = matrixName;
        this.polygon = polygon;
        this.isDefault = isDefault;
    }

    public String getAreaName() {
        return areaName;
    }

    public String getMatrixName() {
        return matrixName;
    }

    public Geometry getPolygon() {
        return polygon;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public static String calcAreaInsert(String schema, String polygon) {
        return String.format(
            "WITH ca AS (" +
                "INSERT INTO %1$s.calculationarea(carea_name, carea_default_sensm_id, carea_default, carea_atype_id) " +
                "VALUES (?, ?, ?, ?) RETURNING carea_id) " +
            "INSERT INTO %1$s.capolygon(cap_carea_id, cap_polygon) " +
            "SELECT ca.carea_id, '%2$s' FROM ca", schema, polygon);
    }

    public static String additionalMatrixCouplingInsert(String schema, int areaId, List<Integer> matrixIds) {
        return String.format(
            "INSERT INTO %s.calcareasensmatrix (casen_carea_id, casen_sensm_id) VALUES %s", schema,
            matrixIds.stream().map(mxId ->
                String.format("(%d, %d)", areaId, mxId))
                .collect(Collectors.joining(",")));
    }

    public Integer getAreaType() {
        return areaType;
    }

    public void setAreaType(Integer areaType) {
        this.areaType = areaType;
    }
}
