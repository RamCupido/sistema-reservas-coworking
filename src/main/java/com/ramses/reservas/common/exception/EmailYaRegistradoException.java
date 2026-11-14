package com.ramses.reservas.common.exception;

public class EmailYaRegistradoException extends ConflictoException {

    public EmailYaRegistradoException(String email) {
        super("Email ya registrado", "El email " + email + " ya está registrado");
    }
}
