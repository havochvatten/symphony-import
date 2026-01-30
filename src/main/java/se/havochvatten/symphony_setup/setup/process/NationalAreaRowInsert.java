package se.havochvatten.symphony_setup.setup.process;

import java.io.FileInputStream;

public class NationalAreaRowInsert {
    public static final String TYPE_TYPES = "TYPES";
    public static final String TYPE_BOUNDARY = "BOUNDARY";

    private final String nationalAreaType;
    private final String countryISO;
    private final String polygonPath;

    public String getNationalAreaType() {
        return nationalAreaType;
    }

    public String getCountryISO() {
        return countryISO;
    }

    public String getPolygon() {
        try (FileInputStream fs = new FileInputStream(this.polygonPath)) {
            return new String(fs.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Error reading polygon file.", e);
        }
    }

    public NationalAreaRowInsert(String nationalAreaType, String countryISO, String polygonPath) {
        this.nationalAreaType = nationalAreaType;
        this.countryISO = countryISO;
        this.polygonPath = polygonPath;
    }

    public static String cleanQuery(String schema) {
        return String.format("DELETE FROM %s.nationalarea WHERE narea_countryiso3 = ? AND narea_type = ?", schema);
    }

    public static String insertQuery(String schema) {
        return String.format("INSERT INTO %s.nationalarea (narea_countryiso3, narea_areas, narea_type) VALUES (?, ?, ?)", schema);
    }
}
