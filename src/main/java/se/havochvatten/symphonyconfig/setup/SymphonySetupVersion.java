package se.havochvatten.symphonyconfig.setup;

public enum SymphonySetupVersion {
        // Enumeration class for available options documentation.
        // Update as necessary only when introducing options.
    v1_0("v1.0"),
    v1_1("v1.1");

    private final String readableVersion;

    SymphonySetupVersion(String readableVersion) {
        this.readableVersion = readableVersion;
    }

    @Override
    public String toString() {
        return readableVersion;
    }
}
