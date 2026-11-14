package com.ramses.reservas.common.exception;

import lombok.Getter;

/**
 * Base de todos los "no encontrado" (→ 404). Con una jerarquía, el manejador global
 * tiene un solo handler por categoría en vez de uno por cada excepción concreta.
 */
@Getter
public abstract class EntidadNoEncontradaException extends RuntimeException {

    private final String titulo;

    protected EntidadNoEncontradaException(String titulo, String detalle) {
        super(detalle);
        this.titulo = titulo;
    }
}
