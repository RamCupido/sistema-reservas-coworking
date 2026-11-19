package com.ramses.reservas.notificacion;

import com.ramses.reservas.reserva.ReservaService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Disparadores de las tareas periódicas. Solo delegan: la lógica está en los services, que se
 * prueban llamándolos directamente (en el perfil test el scheduler está apagado, ver SchedulingConfig).
 * Por defecto Spring ejecuta las tareas en un único hilo, una detrás de otra.
 */
@Component
@RequiredArgsConstructor
public class TareasProgramadas {

    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);

    private final RecordatorioService recordatorioService;
    private final ReservaService reservaService;

    @Scheduled(cron = "${reservas.tareas.cron-recordatorios}")
    public void enviarRecordatorios() {
        int enviados = recordatorioService.enviarPendientes();
        if (enviados > 0) {
            log.info("Recordatorios enviados: {}", enviados);
        }
    }

    @Scheduled(cron = "${reservas.tareas.cron-completar}")
    public void completarReservasFinalizadas() {
        int completadas = reservaService.completarFinalizadas();
        if (completadas > 0) {
            log.info("Reservas marcadas como COMPLETADA: {}", completadas);
        }
    }
}
