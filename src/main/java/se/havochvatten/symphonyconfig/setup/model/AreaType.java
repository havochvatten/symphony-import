package se.havochvatten.symphonyconfig.setup.model;

import javax.annotation.Nullable;

public class AreaType {
    private int id;
    private String name;
    private boolean coastalArea = false;

    public AreaType(int id, String name, @Nullable Boolean coastalArea) {
        this.id = id;
        this.name = name;
        if (coastalArea != null) {
            this.coastalArea = coastalArea;
        }
    }

    public static String getAvailableAreaTypeIdsQuery(String schema) {
        return String.format("SELECT atype_id FROM %s.areatype", schema);
    }
}
