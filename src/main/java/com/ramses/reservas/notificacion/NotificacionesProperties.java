package com.ramses.reservas.notificacion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param habilitadas               false = los correos solo se registran en el log (demo sin SMTP).
 * @param remitente                 "Nombre &lt;correo&gt;" que aparece como From.
 * @param urlBase                   URL pública de la app, para los enlaces del correo.
 * @param anticipacionRecordatorio  cuánto antes del inicio se envía el recordatorio.
 */
@ConfigurationProperties(prefix = "notificaciones")
public record NotificacionesProperties(
        @DefaultValue("true") boolean habilitadas,
        @DefaultValue("Coworking Reservas <no-reply@reservas.local>") String remitente,
        @DefaultValue("http://localhost:8080") String urlBase,
        @DefaultValue("24h") Duration anticipacionRecordatorio) {
}
