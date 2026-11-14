package com.ramses.reservas.common.exception;

import lombok.Getter;

/** Base de las reglas de negocio incumplidas (→ 400): la petición es válida en forma, pero no en contenido. */
@Getter
public abstract class ReglaNegocioException extends RuntimeException {

    private final String titulo;

    protected ReglaNegocioException(String titulo, String detalle) {
        super(detalle);
        this.titulo = titulo;
    }
}
