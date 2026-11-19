package com.ramses.reservas.notificacion;

import com.ramses.reservas.common.exception.ReservaNoEncontradaException;
import com.ramses.reservas.reserva.ReservaMapper;
import com.ramses.reservas.reserva.ReservaRepository;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;
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
 * Arma y envía los correos de una reserva con la plantilla {@code templates/email/reserva.html}.
 * Los métodos lanzan la excepción si el envío falla: quien llama decide qué hacer
 * (el listener la registra; la tarea de recordatorios libera el reclamo para reintentar).
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);
    private static final Locale ESPANOL = Locale.forLanguageTag("es");
    private static final DateTimeFormatter FORMATO_ASUNTO = DateTimeFormatter.ofPattern("dd/MM 'a las' HH:mm");

    private final JavaMailSender mailSender;
    private final ITemplateEngine templateEngine;
    private final ReservaRepository reservaRepository;
    private final ReservaMapper reservaMapper;
    private final NotificacionesProperties properties;

    enum TipoCorreo {
        CONFIRMACION("Reserva confirmada"),
        CANCELACION("Reserva cancelada"),
        RECORDATORIO("Recordatorio de tu reserva");

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

    private void enviar(Long reservaId, TipoCorreo tipo, boolean porAdministrador) {
        ReservaResponse reserva = reservaRepository.findConDetalleById(reservaId)
                .map(reservaMapper::toResponse)
                .orElseThrow(() -> new ReservaNoEncontradaException(reservaId));
        String destinatario = reserva.usuario().email();
        String asunto = tipo.asunto + ": " + reserva.recurso().nombre() + ", "
                + reserva.fechaInicio().format(FORMATO_ASUNTO);

        if (!properties.habilitadas()) {
            log.info("Correo deshabilitado (notificaciones.habilitadas=false). Para: {} | {}", destinatario, asunto);
            return;
        }

        Context contexto = new Context(ESPANOL);
        contexto.setVariable("tipo", tipo.name());
        contexto.setVariable("reserva", reserva);
        contexto.setVariable("porAdministrador", porAdministrador);
        contexto.setVariable("urlBase", properties.urlBase());
        String html = templateEngine.process("email/reserva", contexto);

        mailSender.send(mensaje(destinatario, asunto, html));
        log.info("Correo {} enviado a {} (reserva {})", tipo, destinatario, reservaId);
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
