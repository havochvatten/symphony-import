# Import Baseline Version File Format

The MSP-Symphony Setup Tool supports file-based import of baseline versions via the `-f`/`--file` option.  
Import configuration files may be provided in [**YAML**](https://yaml.org/spec/1.2.2/) (`.yaml`/`.yml`) or [**JSON**](https://www.json.org/json-en.html) (`.json`) format.

All file paths in the configuration are resolved **relative to the config file's location** unless they are absolute.  
Every available configuration option has an equivalent to the cli switches, refer to the CLI documentation for any
additional details.
Apart from the database connection options (`-db`, `-dbH`, `-dbU`, `-dbP`, their `-envDb*`
equivalents, and the defaulted `-dbS` / `-dbPt`), only the GeoTIFF path overrides `-bvpE` and
`-bvpP` may be combined with `-f`. Any other switch is rejected with an error.

Like every other invocation of the tool, a rejected or failed `-f` import exits with status code
`1`; see [Exit codes](README.md#exit-codes) in the README.

## Operations

The required `operation` field selects the import procedure:

| Value           | Description                                         |
|-----------------|-----------------------------------------------------|
| `newBaseline`   | Install a new baseline version                      |
| `update`        | Update data coupled to an existing baseline version |
| `nationalAreas` | Import national areas / boundary polygons           |

The whole configuration is validated before any data is written, and most sections that do not
apply to the declared `operation` are **rejected with an error** rather than silently ignored:

| Operation       | Inapplicable sections                                                     |
|-----------------|--------------------------------------------------------------------------|
| `newBaseline`   | `baseline.id`, `nationalAreas`                                            |
| `update`        | `baseline.name`, `baseline.ecoPath`, `baseline.pressurePath`, `nationalAreas` |
| `nationalAreas` | `baseline`, `metadata`, `matrices`, `calculationAreas`, `csvSettings`     |

Remove the offending section from the configuration file to proceed.

A handful of individual `baseline` fields are the exception and are silently ignored, not
rejected, where they don't apply: `baseline.title`, `baseline.description`, `baseline.locale` and
`baseline.validFrom` for `update`, and `baseline.updateMode` for `newBaseline`.

---

## `newBaseline` - Install a new baseline version

```yaml
operation: newBaseline

baseline:
  name: my-baseline-2025          # Required. Must be unique.
  title: My Baseline 2025         # Optional. Display title (used by the default GUI).
  description: Some description   # Optional.
  validFrom: "2025-01-01"         # Optional. ISO 8601 date. Defaults to today. Must be unique across all
                                  # baseline versions: MSP-Symphony resolves the current baseline by this
                                  # date and fails if two share one.
  locale: en                      # Optional. ISO 639-1 code. Defaults to 'en'.
  ecoPath: data/eco.tiff          # Required*. Path to Ecosystem GeoTIFF.
  pressurePath: data/pressure.tiff  # Required*. Path to Pressure GeoTIFF.

metadata:
  - file: metadata-en.csv
    language: en                  # Optional. Defaults to the baseline locale for the first entry, and to
                                  # the previous entry's language thereafter.
  - file: metadata-sv.csv         # Optional. Multiple languages supported.
    language: sv

matrices:
  - file: matrix-en.csv
    name: Sensitivity matrix 2025   # Required per matrix entry. Calculation areas bind to a
                                    # matrix by name, so 'name' should be unique within the
                                    # baseline version; the tool does not enforce this.
    language: en                    # Optional. Defaults to the baseline locale for the first entry, and
                                    # to the previous entry's language thereafter.
                                    # This selects the language of the band titles used as this CSV's row
                                    # and column headers. Sensitivity matrices are not themselves
                                    # language-specific.

calculationAreas:
  file: areas.gpkg                # Every feature must also carry a 'matrixName' attribute naming an
                                  # already-imported sensitivity matrix. 'areaType' and 'addMatrices' are
                                  # read when present. See the README section on calculation areas for
                                  # the full attribute list.
  nameProperty: name              # Optional. GeoPackage Feature attribute for area "name". Defaults to 'name'.
  defaultAreas:                   # Name the areas to set as default.
    - Area_1
    - Area_3
  # allDefault: true              # Alternative, mutually exclusive with defaultAreas.
                                  # 'allDefault: true' marks every area in the package as a default area.
                                  # In MSP-Symphony a default area is the broad area that supplies a
                                  # region's default matrix; non-default areas are the selectable
                                  # sub-areas shown per area type. Marking everything default leaves no
                                  # selectable areas in the GUI. Use it only for a package holding a
                                  # single national area.

csvSettings:                      # Optional. Override CSV parsing defaults.
  delimiter: ";"                  # Column delimiter. Defaults to ';', the Symphony convention.
  newline: windows                # Set to 'windows' for CRLF input. Omit for LF (the default).
```

> `*` GeoTIFF paths (`ecoPath`, `pressurePath`) may alternatively be supplied or overridden on the command line with `-bvpE` / `-bvpP`.

The `metadata`, `matrices`, `calculationAreas`, and `csvSettings` sections are all optional and can be omitted for a 
minimal baseline shell that is populated later via `update` operations.

---

## `update` - Update data coupled to an existing baseline version

```yaml
operation: update

baseline:
  id: 1                           # Required. Database ID of the target baseline version.
  updateMode: update              # Optional: 'update' (default) or 'replace'.
                                  # 'replace' deletes coupled data on the target baseline version
                                  # before importing: band metadata, sensitivity matrices (user-created
                                  # ones included), calculation areas owned by the baseline version, and
                                  # reliability partition polygons. Read the section below first.

# At least one of metadata / matrices / calculationAreas is required (same shape as newBaseline).
# csvSettings is optional and may accompany any of them, but cannot stand alone.
metadata:
  - file: metadata-en.csv
    language: en
```

### `updateMode: replace` is destructive

`replace` deletes, on the target baseline version, every one of the following before it imports
anything:

* All band metadata and its translated values.
* All sensitivity matrices, user-created ones included, together with every sensitivity score
  they hold.
* All calculation areas owned by the baseline version, together with their polygons and matrix
  couplings.
* All reliability partition polygons.

Ownership of a calculation area is its default sensitivity matrix. An area owned by another
baseline version is not touched, even when it is coupled to a matrix on this one.

The tool counts all of the above before writing anything, and requires explicit confirmation
from the operator whenever that count is non-zero. See the usage documentation for the "update"
option `-u r` (under [Usage](README.md#usage) in the README) for more detail.

Each import runs as a single transaction, so a failure part way through rolls the whole clear
and re-import back rather than leaving the baseline half-cleared. This now holds for metadata,
matrices and calculation areas alike.

Replacing `calculationAreas` together with `metadata`, without also listing `matrices`, will
fail: clearing band metadata cascades to remove every sensitivity matrix on the baseline
version, and with no `matrices` section to reimport them, the calculation areas being imported
cannot resolve the matrix each one references by name. List `matrices` alongside
`calculationAreas` whenever `metadata` is part of the same replace. A replace of
`calculationAreas` on its own, without `metadata` or `matrices`, does not clear matrices and is
unaffected.

---

## `nationalAreas` - Import national areas

```yaml
operation: nationalAreas

nationalAreas:
  - type: BOUNDARY                  # Required. Exactly one BOUNDARY entry must be present.
    file: national-boundary.json
    countryISO: SWE                 # ISO 3166-1 alpha-3. Must match the deployment's 'areas.countrycode'
                                    # application property, or the imported rows will never be read.
                                    # All entries in one file must share the same country.
  - type: COUNTY                    # Additional selectable area types (arbitrary string names).
    file: national-selectable.json
    countryISO: SWE
```

See the [National areas / Boundary polygon](README.md#national-areas--boundary-polygon) section of the README for the
expected structure of the referenced JSON files.

---

## JSON equivalent

All examples above are equally valid with the corresponding structure in JSON.  
For reference, a `newBaseline` example in JSON:

```json
{
  "operation": "newBaseline",

  "baseline": {
    "name": "my-baseline-2025",
    "title": "My Baseline 2025",
    "description": "Some description",
    "validFrom": "2025-01-01",
    "locale": "en",
    "ecoPath": "data/eco.tiff",
    "pressurePath": "data/pressure.tiff"
  },

  "metadata": [
    { "file": "metadata-en.csv", "language": "en" },
    { "file": "metadata-sv.csv", "language": "sv" }
  ],

  "matrices": [
    { "file": "matrix-en.csv", "name": "Sensitivity matrix 2025", "language": "en" }
  ],

  "calculationAreas": {
    "file": "areas.gpkg",
    "nameProperty": "name",
    "defaultAreas": ["Area_1", "Area_3"]
  }
}
```
