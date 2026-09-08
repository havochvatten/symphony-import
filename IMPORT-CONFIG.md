# Import Baseline Version File Format

The MSP-Symphony Setup Tool supports file-based import of baseline versions via the `-f`/`--file` option.  
Import configuration files may be provided in [**YAML**](https://yaml.org/spec/1.2.2/) (`.yaml`/`.yml`) or [**JSON**](https://www.json.org/json-en.html) (`.json`) format.

All file paths in the configuration are resolved **relative to the config file's location** unless they are absolute.  
Every available configuration option has an equivalent to the cli switches, refer to the CLI documentation for any
additional details.
Most options cannot be provided on the command line when using the file-based import mode, the only exception
being the options `-bvpE` and `-bvpP`, as outlined below.

## Operations

The required `operation` field selects the import procedure:

| Value           | Description                                         |
|-----------------|-----------------------------------------------------|
| `newBaseline`   | Install a new baseline version                      |
| `update`        | Update data coupled to an existing baseline version |
| `nationalAreas` | Import national areas / boundary polygons           |

The whole configuration is validated before any data is written, and a section that does not apply
to the declared `operation` is **rejected with an error** rather than silently ignored:

| Operation       | Inapplicable sections                                                     |
|-----------------|--------------------------------------------------------------------------|
| `newBaseline`   | `baseline.id`, `nationalAreas`                                            |
| `update`        | `baseline.name`, `baseline.ecoPath`, `baseline.pressurePath`, `nationalAreas` |
| `nationalAreas` | `baseline`, `metadata`, `matrices`, `calculationAreas`, `csvSettings`     |

Remove the offending section from the configuration file to proceed.

---

## `newBaseline` — Install a new baseline version

```yaml
operation: newBaseline

baseline:
  name: my-baseline-2025          # Required. Must be unique.
  title: My Baseline 2025         # Optional. Display title (used by the default GUI).
  description: Some description   # Optional.
  validFrom: "2025-01-01"         # Optional. ISO 8601 date. Defaults to today's date.
  locale: en                      # Optional. ISO 639-1 code. Defaults to 'en'.
  ecoPath: data/eco.tiff          # Required*. Path to Ecosystem GeoTIFF.
  pressurePath: data/pressure.tiff  # Required*. Path to Pressure GeoTIFF.

metadata:
  - file: metadata-en.csv
    language: en
  - file: metadata-sv.csv         # Optional. Multiple languages supported.
    language: sv

matrices:
  - file: matrix-en.csv
    name: Sensitivity matrix 2025   # Required per matrix entry.
    language: en                    # Optional. Defaults to baseline locale.

calculationAreas:
  file: areas.gpkg
  nameProperty: name              # Optional. GeoPackage Feature attribute for area "name". Defaults to 'name'.
  allDefault: true                # Set all areas provided in the GeoPackage as default. Mutually exclusive with defaultAreas.
  # defaultAreas:                 # Alternative: name specific areas to set as default.
  #   - Area_1
  #   - Area_3

csvSettings:                      # Optional. Override CSV parsing defaults.
  delimiter: ";"                  # Column delimiter (default ',').
  newline: "\n"                   # Row delimiter. Omit for LF.
```

> `*` GeoTIFF paths (`ecoPath`, `pressurePath`) may alternatively be supplied or overridden on the command line with `-bvpE` / `-bvpP`.

The `metadata`, `matrices`, `calculationAreas`, and `csvSettings` sections are all optional and can be omitted for a 
minimal baseline shell that is populated later via `update` operations.

---

## `update` — Update data coupled to an existing baseline version

```yaml
operation: update

baseline:
  id: 1                           # Required. Database ID of the target baseline version.
  updateMode: update              # Optional: 'update' (default) or 'replace'.
                                  # 'replace' clears all coupled data before updating.

# At least one of metadata / matrices / calculationAreas is required (same shape as newBaseline).
# csvSettings is optional and may accompany any of them, but cannot stand alone.
metadata:
  - file: metadata-en.csv
    language: en
```

### `updateMode: replace` is destructive

`replace` deletes every band metadata row on the target baseline version before re-importing.
Two consequences are easy to miss, so the tool checks for both before it writes anything:

* Sensitivity scores are deleted along with their bands. Any sensitivity matrix on the baseline
  version, including matrices users created through the GUI, is left in place but emptied. When
  such user owned matrices exist, the tool prints a warning naming their owners before the
  import confirmation prompt. This cannot be undone by the tool.
* Reliability partition polygons reference band metadata with no cascade, so the delete cannot
  complete while any exist. The tool refuses the run outright and changes nothing. Remove the
  reliability partitions first, or use `updateMode: update`.

The clear and the re-import of a given metadata file run as a single transaction, so a failure
part way through rolls back rather than leaving a half emptied baseline version.

---

## `nationalAreas` — Import national areas

```yaml
operation: nationalAreas

nationalAreas:
  - type: BOUNDARY                  # Required. Exactly one BOUNDARY entry must be present.
    file: national-boundary.json
    countryISO: SWE                 # ISO 3166-1 alpha-3 country code.
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
    "allDefault": true
  }
}
```
