package com.ramses.reservas.common.exception;

import java.time.Duration;

public class CancelacionFueraDePlazoException extends ReglaNegocioException {

    public CancelacionFueraDePlazoException(Duration anticipacionMinima) {
        super("Cancelación fuera de plazo",
                "Solo se puede cancelar hasta " + anticipacionMinima.toHours() + " horas antes del inicio");
    }
}
