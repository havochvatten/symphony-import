package se.havochvatten.symphonyconfig.setup.model;

import org.locationtech.jts.geom.Geometry;

public class CalculationArea {
    private final String areaName;
    private final String matrixName;
    private final Geometry polygon;
    private final boolean isDefault;

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
                "INSERT INTO %1$s.calculationarea(carea_name, carea_default_sensm_id, carea_default) " +
                "VALUES (?, ?, ?) RETURNING carea_id) " +
            "INSERT INTO %1$s.capolygon(cap_carea_id, cap_polygon) " +
            "SELECT ca.carea_id, '%2$s' FROM ca", schema, polygon);
    }
}
