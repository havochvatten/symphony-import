package se.havochvatten.symphony;

import se.havochvatten.symphony.setup.database.DbTestInterface;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

public abstract class TestBase {

    private static final String resourcesPath = "src/test/resources/";

    protected static String propertiesPath          = resourcesPath + "test.properties";
    protected static String csvFilePartial          = resourcesPath + "import/metadata-wellformed-partial.csv";
    protected static String csvFileComplete         = resourcesPath + "import/metadata-wellformed-complete.csv";

    protected static String excelFilePartial        = resourcesPath + "import/metadata-wellformed-partial.xlsx";
    protected static String excelFileComplete       = resourcesPath + "import/metadata-wellformed-complete.xlsx";

    protected static String csvFileFaulty1          = resourcesPath + "import/metadata-faulty_bandnumber.csv";
    protected static String xlsxFileFaulty1         = resourcesPath + "import/metadata-faulty_bandnumber.xlsx";

    protected static String xlsxFilePartialEco      = resourcesPath + "import/metadata-wellformed-partial-Eco.xlsx";
    protected static String csvFilePartialPressure  = resourcesPath  + "import/metadata-wellformed-partial-Pressure.csv";

    protected String database = "symphony";
    protected final String dbSchema;
    protected final String dbHost;
    protected final Integer dbPort;
    protected final String dbUser;
    protected final String dbPassword;

    protected DbTestInterface dbInterface = null;
    protected final int bvId;

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

        bvId = getDbInterface().installTestBaselineVersion();
    }

//    @AfterAll
//    public void cleanUp() {
//        getDbInterface().cleanTestBaselineVersion();
//    }
}
