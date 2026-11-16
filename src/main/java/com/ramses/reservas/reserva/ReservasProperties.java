package com.ramses.reservas.reserva;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param zonaHoraria                   zona del coworking: las fechas de la API se interpretan en ella
 *                                      y en ella se evalúa el horario de atención.
 * @param anticipacionMinimaCancelacion un CLIENTE puede cancelar hasta este tiempo antes del inicio.
 */
@ConfigurationProperties(prefix = "reservas")
public record ReservasProperties(
        @DefaultValue("America/Guayaquil") ZoneId zonaHoraria,
        @DefaultValue("2h") Duration anticipacionMinimaCancelacion) {
}
