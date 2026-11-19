package com.ramses.reservas.notificacion;

import com.ramses.reservas.reserva.Reserva;
import com.ramses.reservas.reserva.ReservaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Regla 6: recordatorio por correo 24 h antes del inicio.
 * <p>
 * Para cada candidata: 1) se "reclama" con un UPDATE atómico, 2) se envía, 3) si el envío falla,
 * se libera para reintentar en la siguiente ejecución. Así no hay duplicados aunque la tarea se
 * ejecute dos veces a la vez, y el envío ocurre fuera de cualquier transacción (no deja una
 * conexión de BD bloqueada mientras se habla con el SMTP).
 * <p>
 * No es @Transactional a propósito: cada reclamo se confirma por separado.
 */
@Service
@RequiredArgsConstructor
public class RecordatorioService {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioService.class);

    private final ReservaRepository reservaRepository;
    private final NotificacionService notificacionService;
    private final NotificacionesProperties properties;
    private final Clock clock;

    /** @return cuántos recordatorios se enviaron en esta ejecución. */
    public int enviarPendientes() {
        Instant ahora = Instant.now(clock);
        Duration anticipacion = properties.anticipacionRecordatorio();
        int enviados = 0;

        for (Reserva reserva : reservaRepository.findCandidatasARecordatorio(ahora, ahora.plus(anticipacion))) {
            if (!necesitaRecordatorio(reserva, anticipacion)) {
                continue;
            }
            if (reservaRepository.reclamarRecordatorio(reserva.getId(), ahora) == 0) {
                continue; // otra ejecución se adelantó (o la cancelaron justo ahora)
            }
            try {
                notificacionService.enviarRecordatorio(reserva.getId());
                enviados++;
            } catch (RuntimeException e) {
                log.warn("Falló el recordatorio de la reserva {}; se reintentará", reserva.getId(), e);
                reservaRepository.liberarRecordatorio(reserva.getId());
            }
        }
        return enviados;
    }

    /**
     * Una reserva hecha con menos antelación que la del recordatorio (p. ej. hoy para dentro de
     * 3 horas) no lo recibe: el correo de confirmación acaba de llegar y otro sería ruido.
     */
    static boolean necesitaRecordatorio(Reserva reserva, Duration anticipacion) {
        return !reserva.getFechaCreacion().plus(anticipacion).isAfter(reserva.getFechaInicio());
    }
}
