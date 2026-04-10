package se.havochvatten.symphonyconfig.setup.model.converter;

import org.apache.commons.cli.Converter;
import org.apache.commons.cli.ParseException;
import se.havochvatten.symphonyconfig.setup.SymphonySetup;

public class UpdateModeConverter implements Converter<SymphonySetup.UpdateMode, ParseException> {

    @Override
    public SymphonySetup.UpdateMode apply(String uValue) throws ParseException {
        if (uValue == null ||
            uValue.equalsIgnoreCase("update") ||
            uValue.equalsIgnoreCase("u"))
            return SymphonySetup.UpdateMode.UPDATE;

        if (uValue.equalsIgnoreCase("replace") ||
            uValue.equalsIgnoreCase("r"))
            return SymphonySetup.UpdateMode.REPLACE;

        throw new ParseException(
            "Unrecognized update mode (\"" + uValue + "\" given).\n" +
                "Supported update modes are `update` (default) and `replace` only.");
    }
}
