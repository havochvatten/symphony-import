package se.havochvatten.symphonyconfig;

import se.havochvatten.symphonyconfig.setup.SymphonySetup;

public class SymphonySetupEntry {
    public static void main(String[] args) {
        SymphonySetup setup = new SymphonySetup(args);
        if (setup.hasFailed()) {
            System.exit(1);
        }
    }
}
