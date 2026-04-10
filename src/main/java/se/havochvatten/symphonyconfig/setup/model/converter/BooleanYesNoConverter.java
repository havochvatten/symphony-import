package se.havochvatten.symphonyconfig.setup.model.converter;

import org.apache.commons.cli.Converter;
import org.apache.commons.cli.ParseException;

import java.text.MessageFormat;

public class BooleanYesNoConverter implements Converter<Boolean, ParseException> {
    private final String optionName;

    private static String generalErrorMsg = "The {0}argument can be either 'y' or 'n'";

    public BooleanYesNoConverter() {
        optionName = "";
    }

    public BooleanYesNoConverter(String optionName) {
        this.optionName = optionName;
    }


    @Override
    public Boolean apply(String s) throws ParseException {
        if (s.length() == 1) {
            char c = s.charAt(0);
            if (c == 'y' || c == 'Y') {
                return true;
            }
            if (c == 'n' || c == 'N') {
                return false;
            }
        }
        throw new ParseException(MessageFormat.format(generalErrorMsg, optionName + ' '));
    }
}
