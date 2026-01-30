package se.havochvatten.symphony;

import org.junit.jupiter.api.AfterEach;
import se.havochvatten.symphony.setup.database.DbTestInterface;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

public abstract class TestBase {

    private static final String RESOURCES_PATH = "src/test/resources/";

    protected static String propertiesPath          = RESOURCES_PATH + "test.properties";
    protected static String csvMetaFilePartialSV    = RESOURCES_PATH + "import/metadata-wellformed-partial-sv.csv";
    protected static String csvMetaFileCompleteSV   = RESOURCES_PATH + "import/metadata-wellformed-complete-sv.csv";
    protected static String csvMetaFileCompleteEN   = RESOURCES_PATH + "import/metadata-wellformed-complete-en.csv";

    protected static String excelMetaFilePartial        = RESOURCES_PATH + "import/metadata-wellformed-partial.xlsx";
    protected static String excelMetaFileCompleteSV     = RESOURCES_PATH + "import/metadata-wellformed-complete-sv.xlsx";

    protected static String csvMetaFileFaulty1      = RESOURCES_PATH + "import/metadata-faulty_bandnumber.csv";
    protected static String xlsxMetaFileFaulty1     = RESOURCES_PATH + "import/metadata-faulty_bandnumber.xlsx";

    protected static String xlsxMetaFilePartialEcoSV =      RESOURCES_PATH + "import/metadata-wellformed-partial-Eco-sv.xlsx";
    protected static String csvMetaFilePartialPressureSV =  RESOURCES_PATH + "import/metadata-wellformed-partial-Pressure-sv.csv";

    protected static String csvMatrixFileSV = RESOURCES_PATH + "import/matrix-wellformed-sv.csv";
    protected static String csvMatrixFileEN = RESOURCES_PATH + "import/matrix-wellformed-en.csv";
    protected static String csvMatrixCompleteName   = "Complete sensitivity matrix TEST";

    protected static String nationalAreaBoundary = RESOURCES_PATH + "import/national-area/test-national-boundary.json";
    protected static String nationalAreaSelectable =  RESOURCES_PATH + "import/national-area/test-national-selectable.json";

    protected static String calculationAreaPackage = RESOURCES_PATH + "import/calcarea-package.gpkg";

    protected String database = "symphony";
    protected final String dbSchema;
    protected final String dbHost;
    protected final Integer dbPort;
    protected final String dbUser;
    protected final String dbPassword;
    protected boolean providedBaseline = true;

    protected DbTestInterface dbInterface = null;
    protected final Integer bvId;

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

    public TestBase() {
        Properties properties = getProperties();

        List<String> propertiesList = properties.keySet().stream()
            .map(String::valueOf)
            .filter(p -> p.startsWith("db."))
            .toList();

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

        if (providedBaseline) {
            bvId = getDbInterface().installTestBaselineVersion();
        } else {
            bvId = null;
        }
    }

    @AfterEach
    public void tearDown() {
        getDbInterface().cleanTestBaselineVersion();
        getDbInterface().cleanCalculationAreas();
    }
}

