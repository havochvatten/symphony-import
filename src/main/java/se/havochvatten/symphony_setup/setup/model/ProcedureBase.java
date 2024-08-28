package se.havochvatten.symphony_setup.setup.model;

import org.apache.commons.cli.Option;

import java.util.ArrayList;
import java.util.List;

import static se.havochvatten.symphony_setup.setup.SymphonySetup.options;

public abstract class ProcedureBase implements IValidatedProcedure {
    protected final List<String> missingArgs = new ArrayList<>();
    protected final List<String> validationErrors = new ArrayList<>();
    protected final List<String> validationMessages = new ArrayList<>();
    protected String argMissingDesc = "Required arguments missing";
    protected String validationErrorDesc = "Validation error(s) encountered";
    protected final String parsingMessageBanner = "Notice";

    public boolean validate() {
        return this.missingArgs.isEmpty();
    }

    public String errorMessage() {
        if (!(validationErrors.isEmpty() && missingArgs.isEmpty())) {
            StringBuilder msg = new StringBuilder();
            if (!validationErrors.isEmpty()) {
                msg.append(validationErrorDesc).append(":\n");
                for (String error : validationErrors) {
                    msg.append(error).append("\n");
                }
            }
            if (!missingArgs.isEmpty()) {
                msg.append(argMissingDesc).append(":\n");
                for (String val : missingArgs) {
                    Option mo = options.getOption(val);
                    msg.append("-").append(mo.getLongOpt())
                        .append(", ").append(String.format("[ -%s ]", val))
                        .append(String.format(" (%s)", mo.getDescription()))
                        .append("\n");
                }
            }
            return msg.toString();
        } else {
            return null;
        }
    }

    public String parsingMessage() {
        if (!validationMessages.isEmpty()) {
            StringBuilder msg = new StringBuilder(parsingMessageBanner).append(":\n");
            for (String pnotice : validationMessages) {
                msg.append(pnotice).append("\n");
            }
            return msg.toString();
        }
        return null;
    }
}
