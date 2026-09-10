# MSP-Symphony Setup Tool

## About
Command line utility facilitating installation and update of "baseline versions" for the marine cumulative impact assessment software [MSP-Symphony](https://github.com/havochvatten/MSP-Symphony).  
The principal mode of operation of the utility is to interpret a command that specifies input data in supported formats (eg. csv, xlsx, GeoTIFF) to automatically update the service's datasource, a PostgreSQL database (>= v14 with PostGIS extension), with that data. It can also (via the `-s`/`--status` option) be useful for quickly determining the state of a baseline version.   

Notably, this package substantially utilizes [**Apache Commons CLI**](https://commons.apache.org/proper/commons-cli/) and [**Apache Commons DbUtils**](https://commons.apache.org/proper/commons-dbutils/).  
Other notable dependencies to this project are [**GeoTools**](https://geotools.org/) (for parsing GeoTIFFs and GeoPackage), and [**GraalVM**](https://www.graalvm.org/) (for packaging the tool as a standalone executable).

This program is designed to be invoked on the command line of the machine hosting the application server (typically, [Wildfly](https://www.wildfly.org)) where the Symphony web service is deployed.   
This requirement is due to two things: 
1. the characteristic of the Symphony service that the GeoTIFF data on which the calculations are based must be available on the local file system
2. the setup tool requires access to the same actual GeoTIFF data files 


### Compatibility chart
The tool will work for specific MSP-Symphony release versions as shown in the table below.

| **Setup tool version** | **MSP-Symphony version** |
|------------------------|--------------------------|
| 1.0                    | <center>1.24.0</center>  |
| 1.1                    | <center>1.25.0</center>  |

## Usage
Below is the output of invoking the tool with the `-h` (usage) option, reflowed for width. 
<details><summary>Expand to view full usage instruction.</summary>

```
usage:  symphony-setup-tool [-bv <arg>] [-bvD <arg>] [-bvL <arg>] [-bvN <arg>] [-bvpE <arg>]
[-bvpP <arg>] [-bvT <arg>] [-bvV <arg>] [-caD <arg>] [-caDA] [-caF <arg>] [-caP <arg>]
[-csvN <arg>] [-csvS <arg>] [-db <arg>] [-dbH <arg>] [-dbP <arg>] [-dbPt <arg>] [-dbS <arg>]
[-dbU <arg>] [-envDb <arg>] [-envDbH <arg>] [-envDbP <arg>] [-envDbU <arg>] [-f <arg>] [-h]
[-md <arg>] [-mdL <arg>] [-mx <arg>] [-mxL <arg>] [-mxN <arg>] [-n] [-na <arg>] [-naC <arg>]
[-naP <arg>] [-s] [-u <arg>] [-v]

Command-line utility to manage baseline data for instances of the software
package MSP-Symphony

               Options                    Since             Description        
-n, --newBaseline                        v1.0       Install a new baseline version.                  
                                                    Requires additional options specifying 
                                                    accessible paths for the GeoTIFF data files
                                                    ('-bvpE', '-bvpP') and the '-bvN' option 
                                                    specifying a unique baseline version name.    
                                                    Cannot be combined with either of the 
                                                    options 'u' or 'bv'                   
-f, --file <arg>                         v1.1       Pass a json/yaml configuration file with       
                                                    bundled input parameters instead of separate
                                                    cli options.
                                                    The expected format is documented separately.     
-s, --status                             v1.0       Report status of baseline.
                                                    Incompatible in conjunction with most other 
                                                    options.         
-v, --fullReport                         v1.0       Verbose report.            
                                                    Combine with 'status' option for detailed
                                                    status report.                   
-h, --help                               v1.0       Print this usage instruction.              
-db, --database <arg>                    v1.0       Target database name, required if corresponding
                                                    environment variable is missing.                  
-dbU, --dbUser <arg>                     v1.0       Database user (needs write privileges), 
                                                    required if corresponding environment variable
                                                    is missing.      
-dbP, --dbPassword <arg>                 v1.0       Clear-text password for the database user,
                                                    required if corresponding environment variable
                                                    is missing.      
-dbPt, --dbPort <arg>                    v1.0       Database port (defaults to 5432)                     
-dbS, --dbSchema <arg>                   v1.0       Database schema (defaults to 'symphony')            
-dbH, --dbHost <arg>                     v1.0       Database host (defaults to 'localhost'),
                                                    required if corresponding environment variable
                                                    is missing.      
-envDb, --envDatabase <arg>              v1.1       Environment variable to specify database name,    
                                                    defaults to SYMPHONY_DB_NAME          
-envDbH, --envDatabaseHost <arg>         v1.1       Environment variable to specify database host,    
                                                    defaults to SYMPHONY_DB_HOST          
-envDbU, --envDatabaseUser <arg>         v1.1       Environment variable to specify database 
                                                    username, defaults to SYMPHONY_DB_USER          
-envDbP, --envDatabasePassword <arg>     v1.1       Environment variable to specify database 
                                                    password, defaults to SYMPHONY_DB_PWD                     
-u, --update <arg>                       v1.0       Update an existing baseline version. Must be 
                                                    combined with '-bv' option to specify the 
                                                    target baseline version id.      
                                                    Takes an optional argument which may be 
                                                    specified as ('u'/'update' or  'r'/'replace'), 
                                                    differentiating "update mode".
                                                    - REPLACE MODE:
                                                    When set to 'replace', coupled data for the      
                                                    target baseline version is deleted before the
                                                    update runs.
                                                    There is a subtlety to which content gets
                                                    targeted for removal, depending on the other
                                                    options that accompany the same invocation:
                                                    Called in conjuction with the metadata option 
                                                    (-md ...) - ALL associated content, in addition
                                                    to the band metadata: matrices, calculation areas
                                                    and reliability partitions will also be wiped 
                                                    from the database, regardless of other options.
                                                    Called with the matrix option (-mx ...), all 
                                                    matrices that is associated with the baseline
                                                    version will be removed.
                                                    If calculation area options (-caF etc) are set,
                                                    calculation areas that is coupled via some 
                                                    sensitivity matrix to the targeted baseline 
                                                    version are removed, prior to the insert.
-bv, --baselineVersion <arg>             v1.0       Target baseline version to update. Used in           
                                                    conjunction with the -u option only.              
-md, --metadata <arg>                    v1.0       Path to metadata file to import (csv or xlsx
                                                    format is supported). Multi-valued option, may
                                                    be specified repeatedly for multiple languages:
                                                    when used multivalued it must match with number
                                                    and order of arguments to the '-mdL' option.                   
-mdL, --metadataLang <arg>               v1.0       Metadata language as ISO 639-1 code.               
                                                    Required when multiple metadata files are given  
                                                    ('-md'), order and number must match exactly.       
                                                    For single-file metadata imports this defaults
                                                    to 'en'                      
-na, --nationalArea <arg>                v1.0       National area type, may be given with multiple 
                                                    arguments.                
-naP, --nationalAreaPolygon <arg>        v1.0       National area polygon file.
                                                    Multi-valued, should match number of arguments
                                                    to the '-na' option.             
-naC, --nationalAreaCountryISO <arg>     v1.0       National area country code.
-mx, --matrix <arg>                      v1.0       Path to sensitivity matrix file to import
                                                    (presently only csv format is supported).               
                                                    Potential multi-valued option for more than one  
                                                    matrix.                   
-mxN, --matrixName <arg>                 v1.0       Sensitivity matrix name.   
                                                    Required when importing sensitivity matrix/ces.   
                                                    Potential multi-valued option to match with the  
                                                    number and order of '-mx' options.                  
-mxL, --matrixLang <arg>                 v1.0       Matrix titles language for row/column headers,
                                                    to match with metadata 'title' for the 
                                                    corresponding band. Defaults to 'en'                
-caF, --calcAreaFile <arg>               v1.0       Path to GeoPackage file comprising calculation    
                                                    area polygons. See other documentation for 
                                                    expected format specification.      
-caP, --calcAreaNameProperty <arg>       v1.0       "Name property" to use for calculation area 
                                                    name ('carea_name' column) value in the 
                                                    GeoPackage file specified by '-caF'.
                                                    The default is 'name'.    
-caD, --calcAreaDefault <arg>            v1.0       Default calculation area.  
                                                    Multi-valued option to correspond with area
                                                    names present in '-caF' GeoPackage file,
                                                    specifying default status of the corresponding
                                                    area.
-caDA, --calcAreaAllDefault              v1.0       Specify to set all calculation areas present
                                                    in the GeoPackage file slated for import by the  
                                                    '-caF' option as default for the target 
                                                    baseline.  
-csvS, --delimiter <arg>                 v1.0       Column delimiter character for CSV files.
                                                    Defaults to ';', the Symphony convention.
-csvN, --newline <arg>                   v1.0       Row delimiter for CSV files. Set to
                                                    'windows' for CRLF input; omitted or any
                                                    other value means LF.
-bvN, --baselineVersionName <arg>        v1.0       Baseline version name, required for "new
                                                    baseline" invocations ('-n'). Must be unique.   
-bvT, --baselineVersionTitle <arg>       v1.1       Baseline version title, used in
                                                    conjunction with ('-n').
                                                    Optional.
-bvD, --baselineVersionDesc <arg>        v1.0       Baseline version description, used in 
                                                    conjunction with ('-n').  
                                                    Optional.                 
-bvV, --baselineVersionDate <arg>        v1.0       Baseline version "valid from"-date as ISO 8601    
                                                    (YYYY-MM-DD). Defaults the host system date.     
-bvL, --baselineVersionLocale <arg>      v1.0       Baseline version locale as ISO 639-1 code, used
                                                    in conjunction with ('-n'). Defaults to 'en'.         
-bvpE, --baselineEcoPath <arg>           v1.0       Baseline version ecosystems GeoTIFF path, 
                                                    required for "new baseline" invocations ('-n').                   
-bvpP, --baselinePressurePath <arg>      v1.0       Baseline version pressures GeoTIFF path,
                                                    required for "new baseline" invocations ('-n').                   
```
</details>

### Exit codes
The tool exits with status code `1` on any failure (a rejected invocation, a failed validation, a
database error) and `0` on success, so it can be wrapped in a shell script that checks `$?`.

## File-based import (`-f`)

As an alternative to passing individual command-line options, all import parameters can be bundled into a single **YAML or JSON configuration file** and supplied via the `-f`/`--file` option:

```
symphony-setup-tool -db mydb -dbU user -dbP secret -f /path/to/import-config.yaml
```

See [IMPORT-CONFIG.md](IMPORT-CONFIG.md) for the full format specification with annotated examples.

## Input data format specification
For practical examples of supported formats, refer to the [test resources directory](/src/test/resources/import).

> [!TIP]
> Files containing the word "faulty" in the name indicate that they're invalid by design for testing purposes.  
> All other files in that directory may serve as valid templates.

### Metadata
Metadata files can be provided as CSV or XLSX (Microsoft Excel).  
Rows should correspond to specific bands in the GeoTIFF raster files. 
One language is expected per file (specified by the `-mdL`/`--metadataLang` input parameter).  
Any key value (column header) may be supplied, as long as it conforms to the regular expression `^[a-zA-Z_][a-zA-Z0-9_]*$` in order to be reliably serializable.  

The following columns are mandatory for the procedure and MUST be present in the input table:  
**bandnumber**  
**symphonycategory** &nbsp; and  
**title**

You will probably also want to make sure that
**symphonytheme** &nbsp; and
**default_selected**
is included.  
These aren't mandatory in the strict sense, but if there's an intention to deploy and use Symphony together with its graphical user interface, their inclusion would appear to be mandated in practice.

For example, a pressure / ecosystem component data band that doesn't specify the meta value **symphonytheme** won't have its corresponding "scenario" calculation settings (inclusion/exclusion/value modification) accessible (visible) in the GUI.  
If a value for **default_selected** is omitted, then all eco-components or pressures will be excluded from the calculations by default in the graphical interface. This is probably not the desired initial state. The boolean setting is useful when baseline data contains optional or extraordinary bands which are not intended for inclusion by default.

| Column header / Key  | Mandatory | Description                                                                                                                                                                                                          |
|:---------------------|:---------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **bandnumber**       |     ✔     | The ordinal (1-based) index of the raster<br> data band that the metadata describes.<br>Unique per **symphonycategory**.                                                                                             |
| **symphonycategory** |     ✔     | Strictly one of 'Ecosystem' or 'Pressure'.                                                                                                                                                                           |
| **title**            |     ✔     | The user-readable title of the described<br>data band. Unique per combination of <br>**symphonycategory**+**bandnumber**.                                                                                            |
| **symphonytheme**    |     ⚠     | The 'group' of components to which the<br>described band belongs. Note that bands<br>that don't specify this value won't be<br>accessible in the default web based GUI.                                              |
| **default_selected** |     ⚠     | Boolean value (only the exact values "true", "1", "false" or "0" are valid) determining whether the band will be initialized as 'selected' / 'active' in the default GUI. Omission is effectively parsed as 'false'. |

#### Partial import 
The setup tool allows importing/updating the data source with partial metadata files; where not all bands are represented.  
This feature should in particular be useful for updating (changing) or appending metadata to existing baseline versions.  
However, until metadata is provided for all bands in both GeoTIFF files specified by the baseline version, sensitivity matrix import is not possible. This is due to the expected format of the sensitivity matrix input files,
where row/column headers reference bands by its **title** metadata attribute.

### Sensitivity matrices
Like metadata, sensitivity matrices are expected as tabular data (presently, only csv is supported).  
Column headers must correspond to the metadata **title** property of all ecosystem component bands in the GeoTIFF file containing the rasters of this type.
If the matrix uses titles in a language other than the default ('en'/English) - the corresponding language entry must exist in the metadata. For this, use the `-mdL`/`--matrixLang` to specify the language code.  
The same then applies to the table row headers but for pressure data bands.
A valid sensitivity score must be present in each cell in the table body as a real decimal number between 0 and 1, inclusive.

### Calculation areas
Calculation areas should be provided as a [GeoPackage](www.geopackage.org) file containing the area polygons that are to be coupled to the baseline. All polygons should define two attributes, a 'name' attribute (either indicated by the `-caP`/`--calcAreaNameProperty` or defaulting to "name") and the 'matrixName' which will be used to determine (by 'name' - ie value of `sensitivitymatrix.sensm_name` column) which sensitivity matrix that calculations for spatial extents inside the specified area polygon will apply, by default.

Additionally, calculation area polygons may define two optional properties: 'areaType' and 'addMatrices'. If given, 'areaType' must be an integer value specifying a corresponding "area type" id (`areatype.atype_id` in the database) for the calculation area.  
Please note that "area types" _can not_ presently be imported using this tool (see [Limitations](#limitations)).

The attribute 'addMatrices' is expected to specify one or more comma separated sensitivity matrix names. These will be coupled and available for use with calculations contained in the corresponding polygon.


### National areas / Boundary polygon
The "Boundary" and "National area" concepts in MSP-Symphony are structured in a slightly convoluted and 'ad-hoc' manner, but these are nevertheless necessary for setting up instances that will work with the default UI.
It might usually not be necessary to update these properties more than once per MSP-Symphony instance.
For this reason, national area import options can't be specified in the same invocation as 'baseline dependent' import options. 

Valid input formats for the "national areas" are application-specific JSON structures that differ between "types".
Data corresponding to type 'BOUNDARY' should be structured as illustrated by the "pseudo JSON schematic" below:
```
{
    "areas": [ 
        {
            "name": "Area name"
            "polygon":  {
                // GeoJSON Polygon (https://geojson.org/schema/Polygon.json) 
                // or MultiPolygon (https://geojson.org/schema/MultiPolygon.json)
            } 
        },
        ...
    ]
}
```

All other "national area types" (apart from "BOUNDARY") can be represented by arbitrary strings (eg. "COUNTY", "MSP") as long as they're unique to the instance. 
The corresponding files (all other 'types' as described above) are expected as json data like this: 
```
{
    "type": "SOMETYPE" // corresponding to -na parameter
    "en": "Area type title", 
           // Title for the area type sections as presented in the GUI 
           // for the corresponding language as given by the key. 
           // MSP-Symphony's default GUI presently supports only 
           // ['en', 'fr', 'sv'] 
    "fr": "Titre du type de zone",
    "groups": [
        {
            "en": "Area group title", 
                // Title for an 'area group' in the corresponding language (ISO 639-2)
            "fr": "Titre du groupe de zones",
            "areas": [
                {
                    "name": "Some area", // Selectable area 'name' (not localized)
                    "code": null,        // optional custom property that is used somewhat arbitrarily in the GUI. 
                                         // Please refer to the implementation (frontend source code) for details.
                    "searchdata": null,  // Legacy custom property, unused, should be possible to omit
                    "areaKm2":    null   // Legacy custom property, unused, should be possible to omit
                    "polygon":  {
                        // GeoJSON Polygon (https://geojson.org/schema/Polygon.json) 
                        // or MultiPolygon (https://geojson.org/schema/MultiPolygon.json)
                    }
                },
                ... 
            ]
        },
        ...
    ]
}
```

## Building
The project is built with Maven and JDK 21.  
Run
```
mvn package -DskipTests
```
to produce a 'self contained' .jar archive (bundles all dependencies) to the ./target directory.  
The artifact may be executed as expected using java -jar, assuming JVM 21 is installed:
```
java -jar /path/to/symphony-import-tool-{VERSION}.jar [...options, see Usage]
```

### Native executables with GraalVM
To build native standalone executables, you need:

1. [a distribution of GraalVM](https://www.graalvm.org/downloads/) for JDK 21 on the targeted architecture (Win/Linux x64, for other architectures you'll need to modify the configuration of `native-maven-plugin`, consult the [documentation](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) for details) and that the build environment has either `GRAALVM_HOME` or `JAVA_HOME` set to the local path where the distribution is installed.
2. _(when building on Windows)_ Visual Studio 2022 version 17.1.0 or later ('vcvarsall.bat')

Build the Linux (x64) executable:
```
mvn package -Pnative-linux -DskipTests
```

Build the Windows (x64) executable:
```
mvn package -Pnative-win -DskipTests
```
> [!TIP]
> If you're facing problems when building native images on Windows, possibly your terminal environment isn't providing all necessary utilities for the toolchain.  
> You may try executing the build command on the "x64 Native Tools Command Prompt" in your Visual Studio distribution.

## Testing

Presently the test suite requires a complete on-line instance of the _MSP-Symphony_ database model, and credentials for a user with write permissions to the target schema in said database.  
The credentials and database url + schema is expected to be provided in a file named `test.properties`.  
There is a template file named [`test.properties.example`](src/test/resources/test.properties.example) where the required property keys are pre-filled.  

Most of the test cases installs a dummy baseline to the database that is removed after the test is run along with any inserted sample data.   

>[!CAUTION]
> It is recommended to run the test suite on a dedicated, otherwise empty data source.  
> Specifically, be aware that three test classes delete **all** entries of the `nationalarea` table, not only the entries they created: `ImportNationalAreasTest` and `FileBasedImportTest` do so from within a test, and `FileBasedConfigValidationTest` does so from an `@AfterEach`, that is after every one of its test methods. 

To run the test suite, simply invoke
```
mvn test
```

The command syntax to exclude the tests that clear the national area table is
```
mvn test -Dtest='!ImportNationalAreasTest,!FileBasedImportTest,!FileBasedConfigValidationTest'
```

## Limitations

The import tool presently (as of v1.1) doesn't provide functionality to insert or update 'area types' (used to differentiate matrix couplings and indicate 'coastal areas').  
For instances that require this feature, the db table `areatype` needs to be populated either using other database tools, alternately by utilizing the resource endpoint `/areatype` on a running instance. 

```pgsql
-- example SQL statement to insert an "area type"
INSERT INTO symphony.areatype (atype_id, atype_name, atype_coastalarea) VALUES (1, 'n-område', false);
```

However, the tool does support specifying pre-existing "area type" ids for calculation areas (see the [instruction for Calculation area import](#calculation-areas)).

The import tool (as of v1.1) does not provide functionality to import "reliability partition" polygons.
