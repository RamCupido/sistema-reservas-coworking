package com.ramses.reservas.common.exception;

import java.time.Duration;

public class ModificacionFueraDePlazoException extends ReglaNegocioException {

    public ModificacionFueraDePlazoException(Duration anticipacionMinima) {
        super("Modificación fuera de plazo",
                "Solo se puede modificar hasta " + anticipacionMinima.toHours() + " horas antes del inicio");
    }
}
