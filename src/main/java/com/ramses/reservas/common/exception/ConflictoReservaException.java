package com.ramses.reservas.common.exception;

public class ConflictoReservaException extends ConflictoException {

    public ConflictoReservaException(String nombreRecurso) {
        super("Horario no disponible",
                "El recurso '" + nombreRecurso + "' ya tiene una reserva que se solapa con ese horario");
    }
}
