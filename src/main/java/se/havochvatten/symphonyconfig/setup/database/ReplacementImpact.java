package se.havochvatten.symphonyconfig.setup.database;

import java.util.List;

/**
 * What an update mode 'replace' will permanently delete on one baseline version, counted
 * before anything is written. A record rather than a parameter list so that adding a
 * category later cannot silently shift an existing argument into the wrong slot.
 *
 * @param metadataBands          rows in meta_bands
 * @param metadataValues         rows in meta_values, across every language
 * @param sensitivityMatrices    rows in sensitivitymatrix, tool-imported and user-created alike
 * @param matrixOwners           usernames owning any of those matrices, for the separate warning
 * @param calculationAreas       areas coupled to this baseline version, by default matrix or
 *                               by secondary link, whichever baseline version owns them
 * @param foreignCalculationAreas how many of those areas another baseline version owns
 * @param calculationAreaPolygons polygons belonging to those areas
 * @param reliabilityPartitions  partition polygons bound to this baseline version's bands
 */
public record ReplacementImpact(
    int metadataBands,
    int metadataValues,
    int sensitivityMatrices,
    List<String> matrixOwners,
    int calculationAreas,
    int foreignCalculationAreas,
    int calculationAreaPolygons,
    int reliabilityPartitions) {

    /**
     * True when the replace would delete nothing, so there is nothing to confirm.
     * foreignCalculationAreas takes no part: it counts a subset of calculationAreas, so it
     * cannot be the only non-zero category.
     */
    public boolean isEmpty() {
        return metadataBands == 0
            && metadataValues == 0
            && sensitivityMatrices == 0
            && calculationAreas == 0
            && calculationAreaPolygons == 0
            && reliabilityPartitions == 0;
    }
}
