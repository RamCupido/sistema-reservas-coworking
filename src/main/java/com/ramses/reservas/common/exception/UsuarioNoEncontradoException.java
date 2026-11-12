package com.ramses.reservas.common.exception;

public class UsuarioNoEncontradoException extends RuntimeException {

    public UsuarioNoEncontradoException(String email) {
        super("No existe un usuario con email " + email);
    }
}
