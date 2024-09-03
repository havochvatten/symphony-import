package se.havochvatten.symphony_setup.setup.model;

public interface IValidatedProcedure {
    boolean validate();
    String errorMessage();
}
