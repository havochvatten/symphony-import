package se.havochvatten.symphony_setup.setup.model.converter;

import org.apache.commons.cli.Converter;
import org.apache.commons.cli.ParseException;

public class DefaultMatrixArgConverter implements Converter<Boolean, ParseException> {
    private static String generalErrorMsg = "The default matrix argument can be either 'y' or 'n'";

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
        throw new ParseException(generalErrorMsg);
    }
}
