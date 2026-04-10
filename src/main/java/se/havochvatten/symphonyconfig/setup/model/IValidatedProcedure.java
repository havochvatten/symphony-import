package se.havochvatten.symphonyconfig.setup.model;

public interface IValidatedProcedure {
    boolean validate();
    String errorMessage();
}
