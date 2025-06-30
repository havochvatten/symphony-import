package se.havochvatten.symphony_setup.setup.model;

public class SensitivityMatrix {

    private int baselineVersionId;
    private int id = -1;
    private final Double[][] matrix;

    public record MatrixHealth(){}

    public SensitivityMatrix(int sm_id, int bvId, int ecoSz, int preSz, Sensitivity[] sensitivities){
        id = sm_id;
        baselineVersionId = bvId;

        matrix = new Double[ecoSz][preSz];

        for (Sensitivity sens : sensitivities){
            matrix[sens.getEBandId()][sens.getPBandId()] = sens.getValue();
        }

        for (int e = 0; e < ecoSz; ++e) {
            for (int p = 0; p < preSz; ++p) {
                if (matrix[e][p] == null){

                }
            }
        }
    }

    public int getBaselineVersionId() {
        return baselineVersionId;
    }

    public void setBaselineVersionId(int baselineVersionId) {
        this.baselineVersionId = baselineVersionId;
    }
}
