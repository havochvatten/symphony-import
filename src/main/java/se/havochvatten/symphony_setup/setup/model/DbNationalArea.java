package se.havochvatten.symphony_setup.setup.model;

import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.BeanProcessor;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static se.havochvatten.symphony_setup.setup.SymphonySetup.Util.*;
import static se.havochvatten.symphony_setup.setup.process.NationalAreaRowInsert.*;

public class DbNationalArea extends NationalArea {
    private String typesString;

    public static final ResultSetHandler<List<DbNationalArea>> handler =
        new BeanListHandler<>(DbNationalArea.class,
            new BasicRowProcessor(
                new BeanProcessor(
                    Map.of("narea_id", "id",
                        "narea_type", "type",
                        "narea_areas", "areasJson",
                        "narea_countryiso3", "countryCode",
                        "narea_types", "typesString"))
            )
        );

    public static String allAreasCheapQuery(String schema) {
        return String.format("SELECT narea_id, narea_type, '' as narea_areas, narea_countryiso3, narea_types FROM %s.nationalarea", schema);
    }

    public static void printNationalAreasStatusReport(List<DbNationalArea> nationalAreas) {
        Set<String> isoCodes = nationalAreas.stream().map(na -> na.countryCode).collect(Collectors.toSet());
        List<String> isoCodesMissingBoundary = new ArrayList<>();

        for  (String isoCode : isoCodes) {
            if (nationalAreas.stream().filter(na -> na.type.equals(TYPE_BOUNDARY)).noneMatch(na -> na.countryCode.equals(isoCode))) {
                isoCodesMissingBoundary.add(isoCode);
            }
        }

        System.out.println(STATUS_SECTION_SEPARATOR);
        System.out.println("National areas status (global setting)");
        System.out.println(STATUS_SECTION_SEPARATOR);

        for(String iso3Code : isoCodes) {
            List<DbNationalArea> areas = nationalAreas.stream().filter(na -> na.countryCode.equals(iso3Code)).toList();
            DbNationalArea typesArea = areas.stream().filter(na -> na.getType().equals(TYPE_TYPES)).findFirst().orElse(null);

            System.out.printf("Country code: \"%s\"%n", iso3Code);

            for (DbNationalArea area : areas) {
                if (!area.type.equals(TYPE_TYPES)) {
                    System.out.printf("National areas with type '%s' defined for isocode (%s).%n", area.type, iso3Code);
                }
            }
            if (typesArea == null) {
                System.out.printf("Warning: no 'types' entry defined for isocode (%s).%n", iso3Code);
            } else {
                Set<String> types = typesArea.getTypes();
                Set<String> excludedTypes =
                    areas.stream().map(DbNationalArea::getType)
                              .filter(type ->
                                  !types.contains(type) &&
                                  !(type.equals(TYPE_BOUNDARY) || type.equals(TYPE_TYPES)))
                          .collect(Collectors.toSet());
                if (!excludedTypes.isEmpty()) {
                    for (String excludedType : excludedTypes) {
                        System.out.printf(
                            "Warning: National areas with type '%s' is defined for isocode (%s) " +
                            "but that type is not present in the corresponding 'types' entry.%n" +
                            "Note that these areas won't be rendered in the default UI.%n",
                                excludedType, iso3Code);
                    }
                }
            }

            System.out.println();
        }

        if (!isoCodesMissingBoundary.isEmpty()) {
            System.out.printf("Errors detected: '%s' polygon missing for country code(s): %s",
                TYPE_BOUNDARY, StringUtils.join(isoCodesMissingBoundary, ", "));
            System.out.println();
        }
    }

    public String getTypesString() {
        return typesString;
    }

    public void setTypesString(String typesString) {
        this.typesString = typesString;
    }

    public Set<String> getTypes() {
        return jsArrayToSet(this.typesString);
    }
}
