package com.ramses.reservas.common.exception;

/** El estado de la reserva impide modificarla (cancelada, ya comenzó, o pertenece a una serie semanal). */
public class ReservaNoModificableException extends ConflictoException {

    public ReservaNoModificableException(String detalle) {
        super("Reserva no modificable", detalle);
    }
}
