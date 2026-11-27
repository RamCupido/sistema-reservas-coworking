package com.ramses.reservas.notificacion;

import com.ramses.reservas.reserva.ReservaCanceladaEvent;
import com.ramses.reservas.reserva.ReservaCreadaEvent;
import com.ramses.reservas.reserva.SerieCanceladaEvent;
import com.ramses.reservas.reserva.SerieCreadaEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacciona a los eventos de reserva enviando correos.
 * <ul>
 *   <li>{@code AFTER_COMMIT}: solo si la reserva quedó guardada de verdad. Si la transacción hace
 *       rollback (p. ej. conflicto 23P01), el evento se descarta.</li>
 *   <li>{@code @Async}: se ejecuta en otro hilo; el usuario no espera al servidor SMTP.</li>
 *   <li>Un fallo del correo se registra en el log y NO afecta a la reserva, que ya está confirmada.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class NotificacionListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionListener.class);

    private final NotificacionService notificacionService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCrearReserva(ReservaCreadaEvent evento) {
        try {
            notificacionService.enviarConfirmacion(evento.reservaId());
        } catch (RuntimeException e) {
            log.error("No se pudo enviar la confirmación de la reserva {}", evento.reservaId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCancelarReserva(ReservaCanceladaEvent evento) {
        try {
            notificacionService.enviarCancelacion(evento.reservaId(), evento.porAdministrador());
        } catch (RuntimeException e) {
            log.error("No se pudo enviar la cancelación de la reserva {}", evento.reservaId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCrearSerie(SerieCreadaEvent evento) {
        try {
            notificacionService.enviarConfirmacionSerie(evento.serieId());
        } catch (RuntimeException e) {
            log.error("No se pudo enviar la confirmación de la serie {}", evento.serieId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCancelarSerie(SerieCanceladaEvent evento) {
        try {
            notificacionService.enviarCancelacionSerie(evento.serieId(), evento.reservaIds(),
                    evento.porAdministrador());
        } catch (RuntimeException e) {
            log.error("No se pudo enviar la cancelación de la serie {}", evento.serieId(), e);
        }
    }
}
