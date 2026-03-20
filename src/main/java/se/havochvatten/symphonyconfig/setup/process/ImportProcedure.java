package se.havochvatten.symphonyconfig.setup.process;

import org.apache.commons.cli.ParseException;
import se.havochvatten.symphonyconfig.setup.config.SettingsBase;
import se.havochvatten.symphonyconfig.setup.model.ProcedureBase;

import javax.annotation.Nullable;
import java.util.Scanner;

public abstract class ImportProcedure<T extends SettingsBase> extends ProcedureBase {

    public final T settings;

    protected abstract String getImportItemName();

    protected ImportProcedure(T settings) {
        this.settings = settings;
    }

    public abstract boolean validate();
    public abstract boolean collect();

    protected void process() throws ParseException {
        try {
            if (!settings.validate()) {
                throw new ParseException(settings.errorMessage());
            }

            if (!validate()) {
                throw new ParseException(errorMessage());
            }

            if (!collect()) {
                throw new ParseException(errorMessage());
            }
        } catch (Exception e) {
            throw new ParseException(e.getMessage());
        }
    }

    protected Scanner pendingImportMessage(@Nullable String notice) {
        Scanner prompt = new Scanner(System.in);

        System.out.println(String.format("Pending %s import: \"%s\"", settings.getTypeDescriptor(), getImportItemName()));
        System.out.println(String.format("-------------------%s", "-".repeat(
            settings.getTypeDescriptor().length() + getImportItemName().length())));

        if (notice != null) {
            System.out.println(notice);
        }

        System.out.println("\nProceed with the import? ('y' to confirm)");
        System.out.print("> ");

        return prompt;
    }
}
