package se.havochvatten.symphonyconfig.setup.config;

import se.havochvatten.symphonyconfig.setup.model.BaselineVersion;
import se.havochvatten.symphonyconfig.setup.model.ProcedureBase;

public abstract class SettingsBase extends ProcedureBase {

    public String inputFilePath;

    public final String typeDescriptor;

    public final BaselineVersion baselineVersion;
    public final boolean clear;

    public SettingsBase(BaselineVersion baselineVersion, String inputFilePath, String inputFileOption, String typeDescriptor, boolean clear) {
        this.baselineVersion = baselineVersion;
        this.clear = clear;
        this.typeDescriptor = typeDescriptor;

        if (inputFilePath != null) {
            this.inputFilePath = inputFilePath;
        } else {
            missingArgs.add(inputFileOption);
        }
    }

    public String getTypeDescriptor() {
        return typeDescriptor;
    }
}
