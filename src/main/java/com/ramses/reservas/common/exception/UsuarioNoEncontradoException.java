package com.ramses.reservas.common.exception;

public class UsuarioNoEncontradoException extends EntidadNoEncontradaException {

    public UsuarioNoEncontradoException(String email) {
        super("Usuario no encontrado", "No existe un usuario con email " + email);
    }
}
