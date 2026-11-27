package com.ramses.reservas.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ramses.reservas.common.exception.ConflictoReservaException;
import com.ramses.reservas.common.exception.ConflictoSerieException;
import com.ramses.reservas.common.exception.RecursoNoEncontradoException;
import com.ramses.reservas.common.exception.ReservaInvalidaException;
import com.ramses.reservas.common.exception.ReservaNoCancelableException;
import com.ramses.reservas.common.exception.SerieNoEncontradaException;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.reserva.SerieReservaService.Ocurrencia;
import com.ramses.reservas.reserva.dto.CrearSerieRequest;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import com.ramses.reservas.reserva.dto.SerieResponse;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** "Ahora" fijado: lunes 16/11/2026 09:00 en Guayaquil (UTC-5), igual que en ReservaServiceTest. */
class SerieReservaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    private static final LocalDateTime AHORA = LocalDate.of(2026, 11, 16).atTime(9, 0);
    private static final LocalDate MARTES = LocalDate.of(2026, 11, 17);
    private static final String ANA = "ana@mail.com";
    private static final String BETO = "beto@mail.com";

    private final SerieReservaRepository serieRepository = mock(SerieReservaRepository.class);
    private final ReservaRepository reservaRepository = mock(ReservaRepository.class);
    private final RecursoRepository recursoRepository = mock(RecursoRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ReservasProperties properties = new ReservasProperties(ZONA, Duration.ofHours(2));
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);

    private SerieReservaService service;
    private Recurso sala;
    private Usuario ana;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA);
        service = new SerieReservaService(serieRepository, reservaRepository, recursoRepository, usuarioRepository,
                new ReservaMapper(properties), properties, clock, eventos);

        sala = Recurso.builder().nombre("Sala Andes").tipo(TipoRecurso.SALA_REUNIONES).capacidad(8).activo(true)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build();
        ReflectionTestUtils.setField(sala, "id", 1L);
        ana = new Usuario("Ana", ANA, "hash", Rol.CLIENTE);

        when(recursoRepository.findById(1L)).thenReturn(Optional.of(sala));
        when(usuarioRepository.findByEmail(ANA)).thenReturn(Optional.of(ana));
        when(serieRepository.save(any(SerieReserva.class))).thenAnswer(inv -> {
            SerieReserva serie = inv.getArgument(0);
            ReflectionTestUtils.setField(serie, "id", 3L);
            return serie;
        });
        when(reservaRepository.saveAllAndFlush(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(reservaRepository.findNoCanceladasEnRango(anyLong(), any(), any())).thenReturn(List.of());
    }

    private static CrearSerieRequest pedido(LocalDate dia, int horaInicio, int horaFin, int semanas) {
        return new CrearSerieRequest(1L, dia.atTime(horaInicio, 0), dia.atTime(horaFin, 0), semanas);
    }

    private static Instant instante(LocalDateTime local) {
        return local.atZone(ZONA).toInstant();
    }

    @SuppressWarnings("unchecked")
    private List<Reserva> reservasGuardadas() {
        ArgumentCaptor<List<Reserva>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservaRepository).saveAllAndFlush(captor.capture());
        return captor.getValue();
    }

    @Nested
    class Crear {

        @Test
        void creaUnaReservaPorSemanaConLaMismaFranjaYLigadaALaSerie() {
            SerieResponse respuesta = service.crear(pedido(MARTES, 10, 11, 3), ANA);

            List<Reserva> guardadas = reservasGuardadas();
            assertThat(guardadas).extracting(Reserva::getFechaInicio).containsExactly(
                    Instant.parse("2026-11-17T15:00:00Z"),   // 10:00 en Guayaquil = 15:00 UTC
                    Instant.parse("2026-11-24T15:00:00Z"),
                    Instant.parse("2026-12-01T15:00:00Z"));
            assertThat(guardadas).allSatisfy(r -> {
                assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
                assertThat(r.getSerie().getId()).isEqualTo(3L);
                assertThat(Duration.between(r.getFechaInicio(), r.getFechaFin())).isEqualTo(Duration.ofHours(1));
            });

            assertThat(respuesta.id()).isEqualTo(3L);
            assertThat(respuesta.semanas()).isEqualTo(3);
            assertThat(respuesta.reservas()).extracting(ReservaResponse::fechaInicio)
                    .containsExactly(MARTES.atTime(10, 0), MARTES.plusWeeks(1).atTime(10, 0),
                            MARTES.plusWeeks(2).atTime(10, 0));
            assertThat(respuesta.reservas()).allSatisfy(r -> assertThat(r.serieId()).isEqualTo(3L));
        }

        @Test
        void publicaUnSoloEventoParaTodaLaSerie() {
            service.crear(pedido(MARTES, 10, 11, 8), ANA);

            verify(eventos).publishEvent(new SerieCreadaEvent(3L));
            verify(eventos, never()).publishEvent(any(ReservaCreadaEvent.class));
        }

        @Test
        void lasFechasCruzanElCambioDeMesYDeAno() {
            List<Ocurrencia> ocurrencias = service.generarOcurrencias(
                    LocalDate.of(2026, 12, 22).atTime(9, 0), LocalDate.of(2026, 12, 22).atTime(10, 0), 3);

            assertThat(ocurrencias).extracting(Ocurrencia::dia).containsExactly(
                    LocalDate.of(2026, 12, 22), LocalDate.of(2026, 12, 29), LocalDate.of(2027, 1, 5));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 13, 52})
        void lasSemanasDebenEstarEntre2Y12(int semanas) {
            assertThatThrownBy(() -> service.crear(pedido(MARTES, 10, 11, semanas), ANA))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("entre 2 y 12 semanas");
            verifyNoInteractions(serieRepository);
        }

        @Test
        void aplicaLasReglasDeUnaReservaNormal() {
            assertThatThrownBy(() -> service.crear(pedido(MARTES, 19, 21, 4), ANA))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("horario de atención");
            assertThatThrownBy(() -> service.crear(new CrearSerieRequest(1L, MARTES.atTime(10, 15),
                    MARTES.atTime(11, 0), 4), ANA))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("bloques de 30 minutos");
            verifyNoInteractions(serieRepository);
        }

        @Test
        void noSePuedeEmpezarEnElPasado() {
            LocalDate hoy = AHORA.toLocalDate();

            assertThatThrownBy(() -> service.crear(pedido(hoy, 8, 9, 4), ANA))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("pasado");
        }

        @Test
        void recursoInactivoLanza404() {
            sala.setActivo(false);

            assertThatThrownBy(() -> service.crear(pedido(MARTES, 10, 11, 4), ANA))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        void siUnaFechaChocaNoSeCreaNingunaYSeIndicaCual() {
            // Beto ya tiene la sala el segundo y el cuarto martes, de 10:30 a 11:30.
            Usuario beto = new Usuario("Beto", BETO, "hash", Rol.CLIENTE);
            when(reservaRepository.findNoCanceladasEnRango(anyLong(), any(), any())).thenReturn(List.of(
                    new Reserva(beto, sala, instante(MARTES.plusWeeks(1).atTime(10, 30)),
                            instante(MARTES.plusWeeks(1).atTime(11, 30))),
                    new Reserva(beto, sala, instante(MARTES.plusWeeks(3).atTime(10, 30)),
                            instante(MARTES.plusWeeks(3).atTime(11, 30)))));

            assertThatThrownBy(() -> service.crear(pedido(MARTES, 10, 11, 4), ANA))
                    .isInstanceOf(ConflictoSerieException.class)
                    .hasMessageContaining("24/11/2026, 08/12/2026")
                    .hasMessageContaining("No se ha creado ninguna")
                    .satisfies(e -> assertThat(((ConflictoSerieException) e).getFechas())
                            .containsExactly(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 12, 8)));

            verify(serieRepository, never()).save(any());
            verify(reservaRepository, never()).saveAllAndFlush(anyList());
            verify(eventos, never()).publishEvent(any());
        }

        @Test
        void unaReservaContiguaNoEsConflicto() {
            Usuario beto = new Usuario("Beto", BETO, "hash", Rol.CLIENTE);
            when(reservaRepository.findNoCanceladasEnRango(anyLong(), any(), any())).thenReturn(List.of(
                    new Reserva(beto, sala, instante(MARTES.plusWeeks(1).atTime(11, 0)),
                            instante(MARTES.plusWeeks(1).atTime(12, 0)))));

            service.crear(pedido(MARTES, 10, 11, 4), ANA);

            assertThat(reservasGuardadas()).hasSize(4);
        }

        @Test
        void siOtraPeticionGanaLaCarreraSeTraduceAConflicto() {
            SQLException exclusion = new SQLException("conflicting key value", "23P01");
            when(reservaRepository.saveAllAndFlush(anyList()))
                    .thenThrow(new DataIntegrityViolationException("ex_reservas_solapamiento", exclusion));

            assertThatThrownBy(() -> service.crear(pedido(MARTES, 10, 11, 4), ANA))
                    .isInstanceOf(ConflictoReservaException.class);
            verify(eventos, never()).publishEvent(any());
        }
    }

    @Nested
    class CancelarRestantes {

        private SerieReserva serie;
        private Reserva pasada;
        private Reserva dentroDeDosHoras;
        private Reserva futura;
        private Reserva yaCancelada;

        @BeforeEach
        void serieDeAna() {
            serie = new SerieReserva(ana, sala, 4);
            ReflectionTestUtils.setField(serie, "id", 3L);
            pasada = reserva(10L, AHORA.minusDays(7));
            dentroDeDosHoras = reserva(11L, AHORA.plusHours(1));
            futura = reserva(12L, AHORA.plusDays(7));
            yaCancelada = reserva(13L, AHORA.plusDays(14));
            yaCancelada.cancelar();

            when(serieRepository.findConDetalleById(3L)).thenReturn(Optional.of(serie));
            when(reservaRepository.findBySerieIdOrderByFechaInicio(3L))
                    .thenReturn(List.of(pasada, dentroDeDosHoras, futura, yaCancelada));
        }

        private Reserva reserva(long id, LocalDateTime inicio) {
            Reserva reserva = new Reserva(ana, sala, instante(inicio), instante(inicio.plusHours(1)), serie);
            ReflectionTestUtils.setField(reserva, "id", id);
            return reserva;
        }

        @Test
        void elClienteSoloCancelaLasQueAunEstanEnPlazo() {
            SerieResponse respuesta = service.cancelarRestantes(3L, ANA, false);

            assertThat(pasada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
            assertThat(dentroDeDosHoras.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);   // menos de 2 h
            assertThat(futura.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
            assertThat(respuesta.reservas()).hasSize(4);
            verify(eventos).publishEvent(new SerieCanceladaEvent(3L, List.of(12L), false));
        }

        @Test
        void elAdminTambienCancelaLasDeMenosDeDosHoras() {
            service.cancelarRestantes(3L, "admin@mail.com", true);

            assertThat(dentroDeDosHoras.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
            assertThat(futura.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
            assertThat(pasada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
            verify(eventos).publishEvent(new SerieCanceladaEvent(3L, List.of(11L, 12L), true));
        }

        @Test
        void unaSerieAjenaRespondeComoSiNoExistiera() {
            assertThatThrownBy(() -> service.cancelarRestantes(3L, BETO, false))
                    .isInstanceOf(SerieNoEncontradaException.class);
            assertThat(futura.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        }

        @Test
        void siNoQuedaNadaQueCancelarAvisaYNoPublicaEvento() {
            futura.cancelar();

            assertThatThrownBy(() -> service.cancelarRestantes(3L, ANA, false))
                    .isInstanceOf(ReservaNoCancelableException.class)
                    .hasMessageContaining("No quedan fechas");
            verify(eventos, never()).publishEvent(any());
        }
    }
}
