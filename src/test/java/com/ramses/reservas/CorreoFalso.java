package com.ramses.reservas;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Doble de prueba del SMTP: en vez de enviar, guarda los mensajes en memoria.
 * <p>
 * Extiende JavaMailSenderImpl, así createMimeMessage() y el MimeMessageHelper funcionan igual que en
 * producción; solo cambia el último paso (doSend). Es thread-safe porque los correos de los eventos
 * se envían desde otro hilo (@Async).
 */
public class CorreoFalso extends JavaMailSenderImpl {

    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(5);

    private final List<MimeMessage> enviados = new CopyOnWriteArrayList<>();
    private final AtomicInteger intentos = new AtomicInteger();
    private volatile boolean fallar;

    @Override
    protected void doSend(MimeMessage[] mensajes, Object[] originales) {
        intentos.incrementAndGet();
        if (fallar) {
            throw new MailSendException("SMTP caído (simulado)");
        }
        enviados.addAll(Arrays.asList(mensajes));
    }

    public void reiniciar() {
        enviados.clear();
        intentos.set(0);
        fallar = false;
    }

    public void simularFallo() {
        fallar = true;
    }

    public int intentos() {
        return intentos.get();
    }

    public List<MimeMessage> enviadosA(String email) {
        return enviados.stream().filter(m -> destinatario(m).equalsIgnoreCase(email)).toList();
    }

    /** Espera (con límite) a que lleguen al menos {@code cantidad} correos para ese destinatario. */
    public List<MimeMessage> esperarCorreosPara(String email, int cantidad) {
        esperarHasta(() -> enviadosA(email).size() >= cantidad);
        List<MimeMessage> recibidos = enviadosA(email);
        if (recibidos.size() < cantidad) {
            throw new AssertionError("Se esperaban " + cantidad + " correos para " + email
                    + " y llegaron " + recibidos.size());
        }
        return recibidos;
    }

    /**
     * Para comprobar que NO llega nada: no hay evento que esperar, así que se da un margen breve
     * al hilo asíncrono antes de afirmar que no hubo envío.
     */
    public void darMargenAlEnvioAsincrono() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void esperarIntentos(int cantidad) {
        esperarHasta(() -> intentos.get() >= cantidad);
    }

    private static void esperarHasta(java.util.function.BooleanSupplier condicion) {
        long limite = System.nanoTime() + ESPERA_MAXIMA.toNanos();
        while (!condicion.getAsBoolean() && System.nanoTime() < limite) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static String destinatario(MimeMessage mensaje) {
        try {
            return ((InternetAddress) mensaje.getRecipients(Message.RecipientType.TO)[0]).getAddress();
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String asunto(MimeMessage mensaje) {
        try {
            return mensaje.getSubject();
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String html(MimeMessage mensaje) {
        try {
            return (String) mensaje.getContent();
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
