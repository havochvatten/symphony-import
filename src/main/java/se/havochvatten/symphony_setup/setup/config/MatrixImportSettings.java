package se.havochvatten.symphony_setup.setup.config;

import se.havochvatten.symphony_setup.setup.model.BaselineVersion;

public class MatrixImportSettings extends BandsBasedSettingsBase {

    private String matrixName;
    private Integer areaId;

    public MatrixImportSettings(BaselineVersion baselineVersion, String inputFilePath, String language, String defaultLanguage, boolean clear, int order) throws Exception {
        super(baselineVersion, inputFilePath, "mx", "matrix", language, defaultLanguage, clear, order);

        argMissingDesc      = "Insufficient arguments for carrying out the sensitivity matrix import procedure";
        validationErrorDesc = "Sensitivity matrix import - invalid settings";
    }

    public String getMatrixName() {
        return matrixName;
    }

    public void setMatrixName(String matrixName) {
        this.matrixName = matrixName;
    }

    public int getAreaId() {
        return areaId;
    }

    public void setAreaId(Integer areaId) {
        this.areaId = areaId;
    }
}
