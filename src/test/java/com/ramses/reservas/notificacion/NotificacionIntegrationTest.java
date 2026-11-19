package com.ramses.reservas.notificacion;

import static com.ramses.reservas.CorreoFalso.asunto;
import static com.ramses.reservas.CorreoFalso.html;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ramses.reservas.CorreoFalso;
import com.ramses.reservas.IntegracionTest;
import com.ramses.reservas.LimpiadorBaseDatos;
import com.ramses.reservas.common.exception.ConflictoReservaException;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.reserva.EstadoReserva;
import com.ramses.reservas.reserva.Reserva;
import com.ramses.reservas.reserva.ReservaRepository;
import com.ramses.reservas.reserva.ReservaService;
import com.ramses.reservas.reserva.dto.CrearReservaRequest;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Correos de punta a punta contra PostgreSQL real: eventos tras el commit, envío asíncrono,
 * recordatorios sin duplicados y paso a COMPLETADA. El SMTP es {@link CorreoFalso}.
 */
@IntegracionTest
class NotificacionIntegrationTest {

    private static final String NORA = "nora@mail.com";
    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");

    @Autowired
    private ReservaService reservaService;
    @Autowired
    private RecordatorioService recordatorioService;
    @Autowired
    private ReservaRepository reservaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private RecursoRepository recursoRepository;
    @Autowired
    private LimpiadorBaseDatos limpiadorBaseDatos;
    @Autowired
    private CorreoFalso correo;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Usuario nora;
    private Recurso sala;
    private final LocalDate dia = LocalDate.now(ZONA).plusDays(5);

    @BeforeEach
    void setUp() {
        limpiadorBaseDatos.limpiar();
        correo.reiniciar();
        nora = usuarioRepository.save(new Usuario("Nora", NORA, "x", Rol.CLIENTE));
        usuarioRepository.save(new Usuario("Omar", "omar@mail.com", "x", Rol.CLIENTE));
        sala = recursoRepository.save(Recurso.builder().nombre("Sala Andes").tipo(TipoRecurso.SALA_REUNIONES)
                .capacidad(8).precioPorHora(new BigDecimal("25.00")).activo(true)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build());
    }

    private ReservaResponse reservar(String email, LocalTime inicio, LocalTime fin) {
        return reservaService.crear(new CrearReservaRequest(sala.getId(), dia.atTime(inicio), dia.atTime(fin)), email);
    }

    @Nested
    class AlReservarYCancelar {

        @Test
        void alCrearUnaReservaLlegaLaConfirmacionConLosDetalles() {
            ReservaResponse reserva = reservar(NORA, LocalTime.of(10, 0), LocalTime.of(11, 30));

            MimeMessage mensaje = correo.esperarCorreosPara(NORA, 1).getFirst();
            assertThat(asunto(mensaje)).startsWith("Reserva confirmada: Sala Andes");
            assertThat(html(mensaje))
                    .contains("Hola <strong>Nora</strong>")
                    .contains("10:00 – 11:30")
                    .contains("$<span>37.50</span>")
                    .contains("#" + reserva.id())
                    .contains("http://localhost:8080/mis-reservas");
        }

        @Test
        void siLaTransaccionHaceRollbackNoSeEnviaNada() {
            // Simula un fallo posterior dentro de la misma transacción: AFTER_COMMIT nunca llega.
            transactionTemplate.executeWithoutResult(estado -> {
                reservar(NORA, LocalTime.of(10, 0), LocalTime.of(11, 0));
                estado.setRollbackOnly();
            });

            correo.darMargenAlEnvioAsincrono();
            assertThat(correo.intentos()).isZero();
            assertThat(reservaRepository.count()).isZero();
        }

        @Test
        void unaReservaRechazadaPorConflictoNoEnviaCorreo() {
            reservar(NORA, LocalTime.of(10, 0), LocalTime.of(11, 0));
            correo.esperarCorreosPara(NORA, 1);

            assertThatThrownBy(() -> reservar("omar@mail.com", LocalTime.of(10, 30), LocalTime.of(11, 30)))
                    .isInstanceOf(ConflictoReservaException.class);

            correo.darMargenAlEnvioAsincrono();
            assertThat(correo.enviadosA("omar@mail.com")).isEmpty();
        }

        @Test
        void laCancelacionPropiaYLaDelAdminTienenTextosDistintos() {
            ReservaResponse propia = reservar(NORA, LocalTime.of(10, 0), LocalTime.of(11, 0));
            ReservaResponse otra = reservar(NORA, LocalTime.of(12, 0), LocalTime.of(13, 0));
            correo.esperarCorreosPara(NORA, 2);

            reservaService.cancelar(propia.id(), NORA, false);
            reservaService.cancelar(otra.id(), "admin@mail.com", true);

            List<String> cancelaciones = correo.esperarCorreosPara(NORA, 4).stream()
                    .filter(m -> asunto(m).startsWith("Reserva cancelada"))
                    .map(CorreoFalso::html)
                    .toList();
            assertThat(cancelaciones).hasSize(2);
            assertThat(cancelaciones).anySatisfy(h -> assertThat(h).contains("tal como solicitaste"));
            assertThat(cancelaciones).anySatisfy(h -> assertThat(h).contains("La administración del coworking"));
        }

        @Test
        void unSmtpCaidoNoAfectaALaReserva() {
            correo.simularFallo();

            ReservaResponse reserva = reservar(NORA, LocalTime.of(10, 0), LocalTime.of(11, 0));

            correo.esperarIntentos(1);
            assertThat(correo.intentos()).isEqualTo(1);
            assertThat(reservaRepository.findById(reserva.id()).orElseThrow().getEstado())
                    .isEqualTo(EstadoReserva.CONFIRMADA);
        }
    }

    @Nested
    class Recordatorios {

        /** Inserta sin pasar por el service (sin evento) y ajusta la fecha de creación en la BD. */
        private Reserva reservaQueEmpiezaEn(Duration empiezaEn, Duration creadaHace) {
            Instant inicio = Instant.now().plus(empiezaEn).truncatedTo(ChronoUnit.MINUTES);
            Reserva reserva = reservaRepository.save(new Reserva(nora, sala, inicio, inicio.plus(Duration.ofHours(1)),
                    new BigDecimal("25.00")));
            jdbcTemplate.update("UPDATE reservas SET fecha_creacion = ? WHERE id = ?",
                    Timestamp.from(Instant.now().minus(creadaHace)), reserva.getId());
            return reserva;
        }

        private Instant recordatorioEnviadoEn(Long id) {
            return reservaRepository.findById(id).orElseThrow().getRecordatorioEnviadoEn();
        }

        @Test
        void seEnviaUnaSolaVezAunqueLaTareaSeEjecuteDosVeces() {
            Reserva reserva = reservaQueEmpiezaEn(Duration.ofHours(20), Duration.ofDays(3));

            assertThat(recordatorioService.enviarPendientes()).isEqualTo(1);
            assertThat(recordatorioService.enviarPendientes()).isZero();

            List<MimeMessage> recibidos = correo.enviadosA(NORA);
            assertThat(recibidos).hasSize(1);
            assertThat(asunto(recibidos.getFirst())).startsWith("Recordatorio de tu reserva: Sala Andes");
            assertThat(html(recibidos.getFirst())).contains("en menos de 24 horas");
            assertThat(recordatorioEnviadoEn(reserva.getId())).isNotNull();
        }

        @Test
        void noSeEnviaSiFaltaMasDe24Horas() {
            reservaQueEmpiezaEn(Duration.ofHours(30), Duration.ofDays(3));

            assertThat(recordatorioService.enviarPendientes()).isZero();
        }

        @Test
        void noSeEnviaAUnaReservaHechaConMenosDe24HorasDeAntelacion() {
            reservaQueEmpiezaEn(Duration.ofHours(5), Duration.ofHours(1));

            assertThat(recordatorioService.enviarPendientes()).isZero();
        }

        @Test
        void noSeEnviaAUnaReservaCancelada() {
            Reserva reserva = reservaQueEmpiezaEn(Duration.ofHours(20), Duration.ofDays(3));
            reserva.cancelar();
            reservaRepository.save(reserva);

            assertThat(recordatorioService.enviarPendientes()).isZero();
        }

        @Test
        void siElEnvioFallaSeReintentaEnLaSiguienteEjecucion() {
            Reserva reserva = reservaQueEmpiezaEn(Duration.ofHours(20), Duration.ofDays(3));
            correo.simularFallo();

            assertThat(recordatorioService.enviarPendientes()).isZero();
            assertThat(recordatorioEnviadoEn(reserva.getId())).isNull();

            correo.reiniciar();
            assertThat(recordatorioService.enviarPendientes()).isEqualTo(1);
        }

        @Test
        void cancelarDespuesDelRecordatorioNoBorraLaMarca() {
            // La columna es de solo lectura para Hibernate: el UPDATE de cancelar() no la pisa.
            Reserva reserva = reservaQueEmpiezaEn(Duration.ofHours(20), Duration.ofDays(3));
            recordatorioService.enviarPendientes();

            reservaService.cancelar(reserva.getId(), "admin@mail.com", true);

            assertThat(recordatorioEnviadoEn(reserva.getId())).isNotNull();
        }
    }

    @Nested
    class Completar {

        private Reserva reserva(Duration inicioDesdeAhora, Duration duracion) {
            Instant inicio = Instant.now().plus(inicioDesdeAhora).truncatedTo(ChronoUnit.MINUTES);
            return reservaRepository.save(new Reserva(nora, sala, inicio, inicio.plus(duracion), BigDecimal.TEN));
        }

        private Reserva recargar(Reserva reserva) {
            return reservaRepository.findById(reserva.getId()).orElseThrow();
        }

        @Test
        void soloLasConfirmadasQueYaTerminaronPasanACompletada() {
            Reserva terminada = reserva(Duration.ofHours(-3), Duration.ofHours(1));
            Reserva enCurso = reserva(Duration.ofMinutes(-30), Duration.ofHours(1));
            Reserva futura = reserva(Duration.ofHours(5), Duration.ofHours(1));
            Reserva canceladaPasada = reserva(Duration.ofHours(-6), Duration.ofHours(1));
            canceladaPasada.cancelar();
            reservaRepository.save(canceladaPasada);

            assertThat(reservaService.completarFinalizadas()).isEqualTo(1);

            assertThat(recargar(terminada).getEstado()).isEqualTo(EstadoReserva.COMPLETADA);
            assertThat(recargar(terminada).getVersion()).isEqualTo(terminada.getVersion() + 1);
            assertThat(recargar(enCurso).getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
            assertThat(recargar(futura).getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
            assertThat(recargar(canceladaPasada).getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        }
    }
}
