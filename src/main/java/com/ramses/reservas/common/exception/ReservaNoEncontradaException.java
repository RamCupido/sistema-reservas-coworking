package com.ramses.reservas.common.exception;

/** También se usa cuando la reserva existe pero es de otro usuario: así no se revela que existe (IDOR). */
public class ReservaNoEncontradaException extends EntidadNoEncontradaException {

    public ReservaNoEncontradaException(Long id) {
        super("Reserva no encontrada", "No existe una reserva con id " + id);
    }
}
