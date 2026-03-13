package se.havochvatten.symphony;

import org.junit.jupiter.api.AfterEach;
import se.havochvatten.symphony.setup.database.DbTestInterface;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public abstract class TestBase {

    protected static final String RESOURCES_PATH = "src/test/resources/";

    protected static final String propertiesPath          = RESOURCES_PATH + "test.properties";
    protected static final String csvMetaFilePartialSV    = RESOURCES_PATH + "import/metadata-wellformed-partial-sv.csv";
    protected static final String csvMetaFileCompleteSV   = RESOURCES_PATH + "import/metadata-wellformed-complete-sv.csv";
    protected static final String csvMetaFileCompleteEN   = RESOURCES_PATH + "import/metadata-wellformed-complete-en.csv";

    protected static final String excelMetaFilePartial        = RESOURCES_PATH + "import/metadata-wellformed-partial-sv.xlsx";
    protected static final String excelMetaFileCompleteSV     = RESOURCES_PATH + "import/metadata-wellformed-complete-sv.xlsx";

    protected static final String csvMetaFileFaulty1      = RESOURCES_PATH + "import/metadata-faulty_bandnumber.csv";
    protected static final String xlsxMetaFileFaulty1     = RESOURCES_PATH + "import/metadata-faulty_bandnumber.xlsx";

    protected static final String xlsxMetaFilePartialEcoSV =      RESOURCES_PATH + "import/metadata-wellformed-partial-Eco-sv.xlsx";
    protected static final String csvMetaFilePartialPressureSV =  RESOURCES_PATH + "import/metadata-wellformed-partial-Pressure-sv.csv";

    protected static final String csvMatrixFileSV = RESOURCES_PATH + "import/matrix-wellformed-sv.csv";
    protected static final String csvMatrixFileEN = RESOURCES_PATH + "import/matrix-wellformed-en.csv";
    protected static final String csvMatrixCompleteName   = "Complete sensitivity matrix TEST";

    protected static final String nationalAreaBoundary = RESOURCES_PATH + "import/national-area/test-national-boundary.json";
    protected static final String nationalAreaSelectable =  RESOURCES_PATH + "import/national-area/test-national-selectable.json";

    protected static final String calculationAreaPackage = RESOURCES_PATH + "import/calcarea-package.gpkg";

    public static final String TEST_TIFF_E_PATH = absoluteResourcePath("/baseline/symphony-import-test-BaselineE.tiff");
    public static final String TEST_TIFF_P_PATH = absoluteResourcePath("/baseline/symphony-import-test-BaselineP.tiff");

    protected static final String TEST_BASELINE_NAME = "test-import-tiff";
    protected static final String TEST_BASELINE_DESC = "Baseline version description";
    protected static final String TEST_VALIDTO_DATE  = "2030-01-01";

    protected String database = "symphony";
    protected final String dbSchema;
    protected final String dbHost;
    protected final Integer dbPort;
    protected final String dbUser;
    protected final String dbPassword;
    protected final boolean provideBaseline;

    protected DbTestInterface dbInterface = null;
    protected Integer bvId;

    static String absoluteResourcePath(String resourcePath) {
        try {
            return new File(TestBase.class.getResource(resourcePath).toURI()).getAbsolutePath();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    protected static String getResourceFileAndReplace(String resourcePath, Map<String, String> replacements) throws IOException {
        String resource = Files.readString(Paths.get(resourcePath));

        for (String replaceToken : replacements.keySet()) {
            resource = resource.replace(replaceToken, replacements.get(replaceToken));
        }

        return resource;
    }

    protected static String getResourceFile(String resourcePath) throws IOException {
        return getResourceFileAndReplace(resourcePath, Map.of());
    }

    protected Properties getProperties() {
        File propertiesFile = new File(propertiesPath);
        Properties properties;

        if (propertiesFile.exists()) {
            properties = new Properties();
            try {
                properties.load(new FileReader(propertiesFile));
            } catch (IOException e) {
                throw new RuntimeException("Error reading properties file: " + propertiesFile.getAbsolutePath());
            }

        } else {
            properties = System.getProperties();
        }

        return properties;
    }

    protected DbTestInterface getDbInterface() {
        if (dbInterface == null) {
            String port = dbPort == null ? null : String.valueOf(dbPort);
            dbInterface = new DbTestInterface(database, dbUser, dbPassword, dbSchema, port, dbHost);
        }
        return dbInterface;
    }

    public TestBase(boolean provideBaseline) {

        Properties properties = getProperties();

        List<String> propertiesList = properties.keySet().stream()
            .map(String::valueOf)
            .filter(p -> p.startsWith("db."))
            .toList();

        this.provideBaseline = provideBaseline;

        boolean hasUser = propertiesList.contains("db.user"), hasPassword = propertiesList.contains("db.password");

        if (!hasUser || !hasPassword) {
            String missingReq = hasUser ? "y `db.password`" :
                (hasPassword ? "y `db.user`" :
                    "ies `db.user` and `db.password`");
            String example = hasUser ? "db.password=\"some-password\"" : "db.user=\"some-user\"";

            throw new RuntimeException(
                String.format("System propert%s must be set to run tests.\n" +
                    "E.g -D%s ...", missingReq, example));

        } else {
            dbUser = properties.getProperty("db.user");
            dbPassword = properties.getProperty("db.password");
        }

        database = propertiesList.contains("db.database") ?
            properties.getProperty("db.database") :                 database;
        dbHost = propertiesList.contains("db.host") ?
            properties.getProperty("db.host") :                     null;
        dbPort = propertiesList.contains("db.port") ?
            Integer.parseInt(properties.getProperty("db.port")) :   null;
        dbSchema = propertiesList.contains("db.schema") ?
            properties.getProperty("db.schema") :                   null;

        if (this.provideBaseline) {
            bvId = getDbInterface().installTestBaselineVersion();
        } else {
            bvId = null;
        }
    }

    @AfterEach
    public void tearDown() {
        if (bvId != null) {
            getDbInterface().cleanBaselineVersion(bvId);
            getDbInterface().cleanCalculationAreas(bvId);
        }
    }
}

