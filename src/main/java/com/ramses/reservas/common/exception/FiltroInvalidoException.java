package com.ramses.reservas.common.exception;

public class FiltroInvalidoException extends ReglaNegocioException {

    public FiltroInvalidoException(String detalle) {
        super("Filtro inválido", detalle);
    }
}
