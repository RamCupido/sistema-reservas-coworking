package com.ramses.reservas.common.exception;

public class RecursoNoEncontradoException extends EntidadNoEncontradaException {

    public RecursoNoEncontradoException(Long id) {
        super("Recurso no encontrado", "No existe un recurso con id " + id);
    }
}
