package com.ramses.reservas.common.exception;

import lombok.Getter;

/** Base de los conflictos con el estado actual (→ 409): duplicados, solapamientos, etc. */
@Getter
public abstract class ConflictoException extends RuntimeException {

    private final String titulo;

    protected ConflictoException(String titulo, String detalle) {
        super(detalle);
        this.titulo = titulo;
    }
}
