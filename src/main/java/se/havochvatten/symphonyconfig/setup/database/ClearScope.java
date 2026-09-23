package se.havochvatten.symphonyconfig.setup.database;

/**
 * How much of a baseline version update mode 'replace' empties before importing. The widths
 * are nested: a band metadata replace necessarily removes the matrices that reference those
 * bands, and a matrix replace necessarily removes the calculation areas that reference those
 * matrices, because calculationarea.carea_default_sensm_id has no ON DELETE action.
 */
public enum ClearScope {
    /** Calculation areas and their polygons only. */
    CALCULATION_AREAS,
    /** Sensitivity matrices and their scores, plus CALCULATION_AREAS. */
    MATRICES,
    /** Band metadata and reliability partitions, plus MATRICES. */
    BAND_METADATA
}
