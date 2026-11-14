package com.ramses.reservas.common.exception;

public class NombreRecursoDuplicadoException extends ConflictoException {

    public NombreRecursoDuplicadoException(String nombre) {
        super("Nombre de recurso duplicado", "Ya existe un recurso llamado '" + nombre + "'");
    }
}
