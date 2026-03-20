package se.havochvatten.symphonyconfig.setup;

import org.apache.commons.cli.Option;

public class SymphonySetupOptionBuilder {

    public static Option newOption(String option, String longOption, boolean hasArg, String description,
         SymphonySetupVersion availableSince) throws IllegalArgumentException {
        Option.Builder builder = Option.builder(option);
        builder.longOpt(longOption);
        builder.desc(description);
        builder.hasArg(hasArg);
        builder.since(availableSince.toString());
        return builder.get();
    }
}
