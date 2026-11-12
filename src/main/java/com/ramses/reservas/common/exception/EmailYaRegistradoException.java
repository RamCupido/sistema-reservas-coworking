package com.ramses.reservas.common.exception;

public class EmailYaRegistradoException extends RuntimeException {

    public EmailYaRegistradoException(String email) {
        super("El email " + email + " ya está registrado");
    }
}
