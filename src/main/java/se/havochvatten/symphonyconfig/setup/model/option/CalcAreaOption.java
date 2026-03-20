package se.havochvatten.symphonyconfig.setup.model.option;

public class CalcAreaOption {
    private Integer existingId;
    private String newAreaName;

    public String getNewAreaName() {
        return newAreaName;
    }

    public void setNewAreaName(String newAreaName) {
        this.newAreaName = newAreaName;
    }

    public Integer getExistingId() {
        return existingId;
    }

    public void setExistingId(Integer existingId) {
        this.existingId = existingId;
    }
}
