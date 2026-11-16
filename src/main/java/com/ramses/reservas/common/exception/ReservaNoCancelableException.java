package com.ramses.reservas.common.exception;

/** El estado actual de la reserva impide cancelarla (ya cancelada, ya comenzó o terminó). */
public class ReservaNoCancelableException extends ConflictoException {

    public ReservaNoCancelableException(String detalle) {
        super("Reserva no cancelable", detalle);
    }
}
