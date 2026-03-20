package se.havochvatten.symphonyconfig.setup.model;

public enum SymphonyCategory {
    ECOSYSTEM("Ecosystem"), PRESSURE("Pressure");

    private final String dbVal;

    SymphonyCategory(String _dbVal) {
        dbVal = _dbVal;
    }

    public String getDbVal() {
        return dbVal;
    }
}

