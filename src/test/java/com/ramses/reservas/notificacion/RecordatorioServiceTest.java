package com.ramses.reservas.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.reserva.Reserva;
import com.ramses.reservas.reserva.ReservaRepository;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

class RecordatorioServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-16T14:00:00Z");
    private static final Duration DIA = Duration.ofHours(24);

    private final ReservaRepository reservaRepository = mock(ReservaRepository.class);
    private final NotificacionService notificacionService = mock(NotificacionService.class);
    private RecordatorioService service;

    @BeforeEach
    void setUp() {
        NotificacionesProperties properties = new NotificacionesProperties(true, "a@b.c", "http://x", DIA);
        service = new RecordatorioService(reservaRepository, notificacionService, properties,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    /** Reserva con id y fecha de creación (normalmente los pone la BD / Hibernate). */
    private static Reserva reserva(long id, Duration empiezaEn, Duration creadaHace) {
        Recurso sala = Recurso.builder().nombre("Sala").tipo(TipoRecurso.SALA_REUNIONES).capacidad(4)
                .precioPorHora(BigDecimal.TEN).activo(true)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build();
        Instant inicio = AHORA.plus(empiezaEn);
        Reserva reserva = new Reserva(new Usuario("Ana", "ana@mail.com", "x", Rol.CLIENTE), sala,
                inicio, inicio.plus(Duration.ofHours(1)), BigDecimal.TEN);
        ReflectionTestUtils.setField(reserva, "id", id);
        ReflectionTestUtils.setField(reserva, "fechaCreacion", AHORA.minus(creadaHace));
        return reserva;
    }

    @Test
    void buscaLasQueEmpiezanEnLasProximas24Horas() {
        service.enviarPendientes();

        verify(reservaRepository).findCandidatasARecordatorio(AHORA, AHORA.plus(DIA));
    }

    @Test
    void reclamaYEnviaCadaCandidata() {
        when(reservaRepository.findCandidatasARecordatorio(any(), any())).thenReturn(List.of(
                reserva(1, Duration.ofHours(20), Duration.ofDays(3)),
                reserva(2, Duration.ofHours(23), Duration.ofDays(2))));
        when(reservaRepository.reclamarRecordatorio(any(), any())).thenReturn(1);

        assertThat(service.enviarPendientes()).isEqualTo(2);
        verify(notificacionService).enviarRecordatorio(1L);
        verify(notificacionService).enviarRecordatorio(2L);
    }

    @Test
    void siOtraEjecucionYaLaReclamoNoLaEnvia() {
        when(reservaRepository.findCandidatasARecordatorio(any(), any()))
                .thenReturn(List.of(reserva(1, Duration.ofHours(20), Duration.ofDays(3))));
        when(reservaRepository.reclamarRecordatorio(1L, AHORA)).thenReturn(0);

        assertThat(service.enviarPendientes()).isZero();
        verify(notificacionService, never()).enviarRecordatorio(any());
    }

    @Test
    void siFallaElEnvioLiberaElReclamoParaReintentar() {
        when(reservaRepository.findCandidatasARecordatorio(any(), any()))
                .thenReturn(List.of(reserva(1, Duration.ofHours(20), Duration.ofDays(3))));
        when(reservaRepository.reclamarRecordatorio(1L, AHORA)).thenReturn(1);
        doThrow(new MailSendException("SMTP caído")).when(notificacionService).enviarRecordatorio(1L);

        assertThat(service.enviarPendientes()).isZero();
        verify(reservaRepository).liberarRecordatorio(1L);
    }

    @Test
    void unaReservaHechaConPocaAntelacionNoRecibeRecordatorio() {
        // Creada hace 1 h para dentro de 5 h: ya recibió la confirmación.
        when(reservaRepository.findCandidatasARecordatorio(any(), any()))
                .thenReturn(List.of(reserva(1, Duration.ofHours(5), Duration.ofHours(1))));

        assertThat(service.enviarPendientes()).isZero();
        verify(reservaRepository, never()).reclamarRecordatorio(any(), any());
    }

    @Test
    void elLimiteDeAntelacionEsInclusivo() {
        // Creada exactamente 24 h antes del inicio → sí; un minuto menos → no.
        assertThat(RecordatorioService.necesitaRecordatorio(reserva(1, Duration.ofHours(10), Duration.ofHours(14)), DIA))
                .isTrue();
        assertThat(RecordatorioService.necesitaRecordatorio(
                reserva(1, Duration.ofHours(10), Duration.ofHours(14).minusMinutes(1)), DIA)).isFalse();
    }
}
