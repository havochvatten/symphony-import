package se.havochvatten.symphony_setup.setup.process;

import se.havochvatten.symphony_setup.setup.config.MatrixImportSettings;

import se.havochvatten.symphony_setup.setup.model.*;

import java.util.*;
import java.util.stream.Collectors;

public abstract class MatrixBase extends ImportProcedure<MatrixImportSettings>  {

    protected List<Sensitivity> sensitivities;
    final Baseline currentBaseline;

    protected MatrixBase(MatrixImportSettings settings, Baseline _baseline) {
        super(settings);
        currentBaseline = _baseline;
    }

    @Override
    protected String getImportItemName() {
        return settings.getMatrixName();
    }

    protected boolean validateMatrixBands(SymphonyCategory category, Set<String> bandKeys) {
        Collection<SymphonyBand> bands = currentBaseline.bandsByCategory(category);

        if (bandKeys.size() != bands.size()) {
            validationErrors.add("Wrong sensitivity matrix dimension");
            return false;
        }

        if (!bands.stream().allMatch(b -> b.getMeta().containsKey(settings.language))) {
            validationErrors.add(
                String.format("Missing meta entry for language (%s)", settings.language));
            return false;
        }

        if (!bandKeys.containsAll(bands.stream().map(
                b -> b.getTitle(settings.language)
            ).collect(Collectors.toSet()))) {
            validationErrors.add("Some missing title(s) in metadata");
            return false;
        }

        return true;
    }

    public boolean confirmImport() {
        Scanner prompt = pendingImportMessage(null);

        return prompt.nextLine().trim().equalsIgnoreCase("y");
    }

    public List<Sensitivity> getSensitivities() {
        return sensitivities;
    }

    public static String insertSensMatrix(String schema) {
        return String.format("INSERT INTO %s.sensitivitymatrix (sensm_name, sensm_bver_id) " +
                             "VALUES (?, ?)", schema);
    }
}
