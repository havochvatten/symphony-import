package se.havochvatten.symphonyconfig.setup;

import jakarta.validation.constraints.NotBlank;
import org.apache.commons.cli.Option;

public class SymphonySetupOptionBuilder {

    public static Option newOption(@NotBlank String option, String longOption, boolean hasArg, String description,
                                   SymphonySetupVersion availableSince) throws IllegalArgumentException {
        if (option == null || option.isBlank()) {
            throw new IllegalArgumentException("All options should define a short form");
        }

        Option.Builder builder = Option.builder(option);
        builder.longOpt(longOption);
        builder.desc(description);
        builder.hasArg(hasArg);
        builder.since(availableSince.toString());
        return builder.get();
    }
}
