package se.havochvatten.symphonyconfig.setup.model.converter;

import org.apache.commons.cli.Converter;
import org.apache.commons.cli.ParseException;
import se.havochvatten.symphonyconfig.setup.model.option.CalcAreaOption;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class MatrixCalcAreaConverter implements Converter<CalcAreaOption, ParseException> {
    private final Set<Integer> existingIds;
    private final Set<String> newCalcAreaNames;

    public MatrixCalcAreaConverter(int[] existingIds, String[] pendingAreaNames) {
        this.existingIds = Arrays.stream(existingIds).boxed().collect(Collectors.toSet());
        this.newCalcAreaNames =
            pendingAreaNames != null ?
            Arrays.stream(pendingAreaNames).collect(Collectors.toSet()) : null;
    }

    @Override
    public CalcAreaOption apply(String s) throws ParseException {
        CalcAreaOption calcAreaOption = new CalcAreaOption();
        try {
            int calcAreaId = Integer.parseInt(s);

            if (!existingIds.contains(calcAreaId)) {
                String errMsg = "Invalid area id: " + calcAreaId + "\n";

                if (existingIds.isEmpty()) {
                    errMsg += "There are no calculation areas tied to the selected baseline.\n";
                } else {
                    errMsg += String.format("Available ids: {0}.\n",
                        existingIds.stream().map(x -> x + "").collect(Collectors.joining(", ")));
                }

                throw new ParseException(errMsg);
            }

            calcAreaOption.setExistingId(calcAreaId);
        } catch (NumberFormatException e) {
            if (newCalcAreaNames != null) {
                if (!newCalcAreaNames.contains(s)) {
                    calcAreaOption.setNewAreaName(s);
                } else {
                    throw new ParseException(
                        "Given calculation area name %s is not found in the package: " +
                            String.join(", \n", newCalcAreaNames)
                            + "\n");
                }
            } else {
                throw new ParseException("");
            }
        }

        return calcAreaOption;
    }
}
