package com.ramses.reservas.notificacion;

import com.ramses.reservas.common.exception.ReservaNoEncontradaException;
import com.ramses.reservas.common.exception.SerieNoEncontradaException;
import com.ramses.reservas.reserva.Reserva;
import com.ramses.reservas.reserva.ReservaMapper;
import com.ramses.reservas.reserva.ReservaRepository;
import com.ramses.reservas.reserva.SerieReserva;
import com.ramses.reservas.reserva.SerieReservaRepository;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Arma y envía los correos de una reserva ({@code templates/email/reserva.html}) o de una serie
 * semanal ({@code templates/email/serie.html}: un solo correo con todas las fechas).
 * Los métodos lanzan la excepción si el envío falla: quien llama decide qué hacer
 * (el listener la registra; la tarea de recordatorios libera el reclamo para reintentar).
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    private static final Locale ESPANOL = Locale.forLanguageTag("es");
    private static final DateTimeFormatter FORMATO_ASUNTO = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");
    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final JavaMailSender mailSender;
    private final ITemplateEngine templateEngine;
    private final ReservaRepository reservaRepository;
    private final SerieReservaRepository serieRepository;
    private final ReservaMapper reservaMapper;
    private final NotificacionesProperties properties;

    enum TipoCorreo {
        CONFIRMACION("Reserva confirmada"),
        CANCELACION("Reserva cancelada"),
        RECORDATORIO("Recordatorio de tu reserva"),
        SERIE_CONFIRMACION("Reserva semanal confirmada"),
        SERIE_CANCELACION("Reserva semanal cancelada");

        private final String asunto;

        TipoCorreo(String asunto) {
            this.asunto = asunto;
        }
    }

    @Transactional(readOnly = true)
    public void enviarConfirmacion(Long reservaId) {
        enviar(reservaId, TipoCorreo.CONFIRMACION, false);
    }

    @Transactional(readOnly = true)
    public void enviarCancelacion(Long reservaId, boolean porAdministrador) {
        enviar(reservaId, TipoCorreo.CANCELACION, porAdministrador);
    }

    @Transactional(readOnly = true)
    public void enviarRecordatorio(Long reservaId) {
        enviar(reservaId, TipoCorreo.RECORDATORIO, false);
    }

    @Transactional(readOnly = true)
    public void enviarConfirmacionSerie(Long serieId) {
        enviarSerie(serieId, reservaRepository.findBySerieIdOrderByFechaInicio(serieId),
                TipoCorreo.SERIE_CONFIRMACION, false);
    }

    /** Solo menciona las fechas que se acaban de cancelar, no las pasadas ni las canceladas antes. */
    @Transactional(readOnly = true)
    public void enviarCancelacionSerie(Long serieId, Collection<Long> reservaIds, boolean porAdministrador) {
        List<Reserva> canceladas = reservaRepository.findBySerieIdOrderByFechaInicio(serieId).stream()
                .filter(r -> reservaIds.contains(r.getId()))
                .toList();
        enviarSerie(serieId, canceladas, TipoCorreo.SERIE_CANCELACION, porAdministrador);
    }

    private void enviarSerie(Long serieId, List<Reserva> reservas, TipoCorreo tipo, boolean porAdministrador) {
        SerieReserva serie = serieRepository.findConDetalleById(serieId)
                .orElseThrow(() -> new SerieNoEncontradaException(serieId));
        if (reservas.isEmpty()) {
            return;
        }
        List<ReservaResponse> fechas = reservas.stream().map(reservaMapper::toResponse).toList();
        ReservaResponse primera = fechas.getFirst();
        String asunto = tipo.asunto + ": " + serie.getRecurso().getNombre() + ", "
                + fechas.size() + (fechas.size() == 1 ? " fecha" : " fechas")
                + " desde el " + primera.fechaInicio().format(FORMATO_DIA);

        Context contexto = new Context(ESPANOL);
        contexto.setVariable("tipo", tipo.name());
        contexto.setVariable("nombre", serie.getUsuario().getNombre());
        contexto.setVariable("recurso", serie.getRecurso().getNombre());
        contexto.setVariable("primera", primera);
        contexto.setVariable("reservas", fechas);
        contexto.setVariable("porAdministrador", porAdministrador);
        contexto.setVariable("urlBase", properties.urlBase());
        procesarYEnviar(serie.getUsuario().getEmail(), asunto, "email/serie", contexto, tipo, "serie " + serieId);
    }

    private void enviar(Long reservaId, TipoCorreo tipo, boolean porAdministrador) {
        ReservaResponse reserva = reservaRepository.findConDetalleById(reservaId)
                .map(reservaMapper::toResponse)
                .orElseThrow(() -> new ReservaNoEncontradaException(reservaId));
        String destinatario = reserva.usuario().email();
        String asunto = tipo.asunto + ": " + reserva.recurso().nombre() + ", "
                + reserva.fechaInicio().format(FORMATO_ASUNTO);

        Context contexto = new Context(ESPANOL);
        contexto.setVariable("tipo", tipo.name());
        contexto.setVariable("reserva", reserva);
        contexto.setVariable("porAdministrador", porAdministrador);
        contexto.setVariable("urlBase", properties.urlBase());
        procesarYEnviar(destinatario, asunto, "email/reserva", contexto, tipo, "reserva " + reservaId);
    }

    private void procesarYEnviar(String destinatario, String asunto, String plantilla, Context contexto,
                                 TipoCorreo tipo, String referencia) {
        if (!properties.habilitadas()) {
            log.info("Correo deshabilitado (notificaciones.habilitadas=false). Para: {} | {}", destinatario, asunto);
            return;
        }
        String html = templateEngine.process(plantilla, contexto);
        mailSender.send(mensaje(destinatario, asunto, html));
        log.info("Correo {} enviado a {} ({})", tipo, destinatario, referencia);
    }

    private MimeMessage mensaje(String destinatario, String asunto, String html) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setFrom(properties.remitente());
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(html, true);
            return mensaje;
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
    }
}
