package com.ramses.reservas.common.exception;

public class HorarioInvalidoException extends ReglaNegocioException {

    public HorarioInvalidoException(String detalle) {
        super("Horario inválido", detalle);
    }
}
