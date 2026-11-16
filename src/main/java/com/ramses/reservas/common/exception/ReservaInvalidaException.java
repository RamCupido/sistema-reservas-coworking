package com.ramses.reservas.common.exception;

/** Horario fuera de atención, bloques no alineados a 30 min, fechas en el pasado, etc. */
public class ReservaInvalidaException extends ReglaNegocioException {

    public ReservaInvalidaException(String detalle) {
        super("Reserva inválida", detalle);
    }
}
