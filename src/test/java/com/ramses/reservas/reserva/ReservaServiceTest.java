package com.ramses.reservas.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ramses.reservas.common.exception.CancelacionFueraDePlazoException;
import com.ramses.reservas.common.exception.ConflictoReservaException;
import com.ramses.reservas.common.exception.FiltroInvalidoException;
import com.ramses.reservas.common.exception.ModificacionFueraDePlazoException;
import com.ramses.reservas.common.exception.RecursoNoEncontradoException;
import com.ramses.reservas.common.exception.ReservaInvalidaException;
import com.ramses.reservas.common.exception.ReservaNoCancelableException;
import com.ramses.reservas.common.exception.ReservaNoEncontradaException;
import com.ramses.reservas.common.exception.ReservaNoModificableException;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.reserva.dto.CrearReservaRequest;
import com.ramses.reservas.reserva.dto.ModificarReservaRequest;
import com.ramses.reservas.reserva.dto.ReservaResponse;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * "Ahora" está fijado con Clock.fixed: lunes 16/11/2026 09:00 en Guayaquil (UTC-5).
 * Sin un reloj inyectable, las reglas de tiempo serían imposibles de probar de forma estable.
 */
class ReservaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 16);
    private static final LocalDateTime AHORA = HOY.atTime(9, 0);
    private static final LocalDate MANANA = HOY.plusDays(1);
    private static final String ANA = "ana@mail.com";

    private final ReservaRepository reservaRepository = mock(ReservaRepository.class);
    private final RecursoRepository recursoRepository = mock(RecursoRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ReservasProperties properties = new ReservasProperties(ZONA, Duration.ofHours(2));
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);

    private ReservaService service;
    private Recurso sala;
    private Usuario ana;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA);
        service = new ReservaService(reservaRepository, recursoRepository, usuarioRepository,
                new ReservaMapper(properties), properties, clock, eventos);

        sala = Recurso.builder().nombre("Sala Andes").tipo(TipoRecurso.SALA_REUNIONES).capacidad(8)
                .activo(true)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build();
        ana = new Usuario("Ana", ANA, "hash", Rol.CLIENTE);

        when(recursoRepository.findById(1L)).thenReturn(Optional.of(sala));
        when(usuarioRepository.findByEmail(ANA)).thenReturn(Optional.of(ana));
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CrearReservaRequest pedido(LocalDateTime inicio, LocalDateTime fin) {
        return new CrearReservaRequest(1L, inicio, fin);
    }

    private static Instant instante(LocalDateTime local) {
        return local.atZone(ZONA).toInstant();
    }

    @Nested
    class Crear {

        @Test
        void reservaValidaQuedaConfirmadaConFechasEnUtc() {
            ReservaResponse respuesta = service.crear(
                    pedido(MANANA.atTime(10, 0), MANANA.atTime(11, 30)), ANA);

            ArgumentCaptor<Reserva> captor = ArgumentCaptor.forClass(Reserva.class);
            verify(reservaRepository).saveAndFlush(captor.capture());
            Reserva guardada = captor.getValue();
            assertThat(guardada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
            // 10:00 en Guayaquil (UTC-5) = 15:00 UTC
            assertThat(guardada.getFechaInicio()).isEqualTo(Instant.parse("2026-11-17T15:00:00Z"));
            // La respuesta vuelve a hora local
            assertThat(respuesta.fechaInicio()).isEqualTo(MANANA.atTime(10, 0));
        }

        @Test
        void reservaQueOcupaTodoElHorarioDeAtencionEsValida() {
            service.crear(pedido(MANANA.atTime(8, 0), MANANA.atTime(20, 0)), ANA);

            verify(reservaRepository).saveAndFlush(any());
        }

        @Test
        void recursoInexistenteLanza404() {
            when(recursoRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.crear(pedido(MANANA.atTime(10, 0), MANANA.atTime(11, 0)), ANA))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        void recursoInactivoNoSePuedeReservar() {
            sala.setActivo(false);

            assertThatThrownBy(() -> service.crear(pedido(MANANA.atTime(10, 0), MANANA.atTime(11, 0)), ANA))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @ParameterizedTest(name = "{2}")
        @CsvSource({
                "11:00, 10:00, fin antes del inicio",
                "10:00, 10:00, duración cero",
                "10:15, 11:00, inicio no alineado a 30 min",
                "10:00, 10:45, fin no alineado a 30 min",
                "10:00, 10:20, menos de 30 minutos",
                "07:30, 09:00, empieza antes de la apertura",
                "19:30, 20:30, termina después del cierre"
        })
        void horarioInvalidoLanzaReservaInvalida(String inicio, String fin, String caso) {
            CrearReservaRequest request = pedido(
                    MANANA.atTime(LocalTime.parse(inicio)), MANANA.atTime(LocalTime.parse(fin)));

            assertThatThrownBy(() -> service.crear(request, ANA)).isInstanceOf(ReservaInvalidaException.class);
            verify(reservaRepository, never()).saveAndFlush(any());
        }

        @Test
        void reservaQueCruzaDeDiaEsInvalida() {
            CrearReservaRequest request = pedido(MANANA.atTime(19, 0), MANANA.plusDays(1).atTime(9, 0));

            assertThatThrownBy(() -> service.crear(request, ANA))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("mismo día");
        }

        @Test
        void reservaEnElPasadoEsInvalida() {
            CrearReservaRequest request = pedido(HOY.atTime(8, 30), HOY.atTime(9, 30)); // ahora son las 09:00

            assertThatThrownBy(() -> service.crear(request, ANA))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("pasado");
        }

        @Test
        void reservaQueEmpiezaJustoAhoraEsInvalida() {
            assertThatThrownBy(() -> service.crear(pedido(HOY.atTime(9, 0), HOY.atTime(10, 0)), ANA))
                    .isInstanceOf(ReservaInvalidaException.class);
        }

        @Test
        void reservaHoyMasTardeEsValida() {
            service.crear(pedido(HOY.atTime(9, 30), HOY.atTime(10, 0)), ANA);

            verify(reservaRepository).saveAndFlush(any());
        }

        @Test
        void solapamientoDetectadoEnLaVerificacionPreviaLanzaConflicto() {
            when(reservaRepository.existeSolapamiento(any(), any(), any())).thenReturn(true);

            assertThatThrownBy(() -> service.crear(pedido(MANANA.atTime(10, 0), MANANA.atTime(11, 0)), ANA))
                    .isInstanceOf(ConflictoReservaException.class);
            verify(reservaRepository, never()).saveAndFlush(any());
        }

        @Test
        void violacionDelConstraintExcludeSeTraduceAConflicto() {
            // Carrera: la verificación previa no vio nada, pero otra transacción insertó antes.
            SQLException exclusion = new SQLException("conflicting key value", "23P01");
            when(reservaRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("ex_reservas_solapamiento", exclusion));

            assertThatThrownBy(() -> service.crear(pedido(MANANA.atTime(10, 0), MANANA.atTime(11, 0)), ANA))
                    .isInstanceOf(ConflictoReservaException.class);
        }

        @Test
        void otraViolacionDeIntegridadNoSeConfundeConSolapamiento() {
            SQLException fk = new SQLException("foreign key", "23503");
            when(reservaRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("fk", fk));

            assertThatThrownBy(() -> service.crear(pedido(MANANA.atTime(10, 0), MANANA.atTime(11, 0)), ANA))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class Cancelar {

        private Reserva reservaDeAnaQueEmpiezaEn(Duration dentroDe) {
            Instant inicio = instante(AHORA).plus(dentroDe);
            Reserva reserva = new Reserva(ana, sala, inicio, inicio.plus(Duration.ofHours(1)));
            when(reservaRepository.findConDetalleById(7L)).thenReturn(Optional.of(reserva));
            return reserva;
        }

        @Test
        void clienteCancelaSuPropiaReserva() {
            Reserva reserva = reservaDeAnaQueEmpiezaEn(Duration.ofDays(1));

            ReservaResponse respuesta = service.cancelar(7L, ANA, false);

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
            assertThat(respuesta.estado()).isEqualTo(EstadoReserva.CANCELADA);
        }

        @Test
        void clienteNoVeReservasAjenasYRecibe404() {
            reservaDeAnaQueEmpiezaEn(Duration.ofDays(1));

            assertThatThrownBy(() -> service.cancelar(7L, "beto@mail.com", false))
                    .isInstanceOf(ReservaNoEncontradaException.class);
        }

        @Test
        void adminCancelaReservasAjenas() {
            Reserva reserva = reservaDeAnaQueEmpiezaEn(Duration.ofDays(1));

            service.cancelar(7L, "admin@mail.com", true);

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        }

        @Test
        void clientePuedeCancelarExactamenteDosHorasAntes() {
            Reserva reserva = reservaDeAnaQueEmpiezaEn(Duration.ofHours(2));

            service.cancelar(7L, ANA, false);

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        }

        @Test
        void clienteNoPuedeCancelarConMenosDeDosHoras() {
            Reserva reserva = reservaDeAnaQueEmpiezaEn(Duration.ofHours(2).minusMinutes(1));

            assertThatThrownBy(() -> service.cancelar(7L, ANA, false))
                    .isInstanceOf(CancelacionFueraDePlazoException.class);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        }

        @Test
        void adminPuedeCancelarConMenosDeDosHoras() {
            Reserva reserva = reservaDeAnaQueEmpiezaEn(Duration.ofMinutes(30));

            service.cancelar(7L, "admin@mail.com", true);

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        }

        @Test
        void noSePuedeCancelarDosVeces() {
            reservaDeAnaQueEmpiezaEn(Duration.ofDays(1)).cancelar();

            assertThatThrownBy(() -> service.cancelar(7L, ANA, false))
                    .isInstanceOf(ReservaNoCancelableException.class)
                    .hasMessageContaining("ya está cancelada");
        }

        @Test
        void niSiquieraElAdminCancelaUnaReservaQueYaEmpezo() {
            reservaDeAnaQueEmpiezaEn(Duration.ofMinutes(-30));

            assertThatThrownBy(() -> service.cancelar(7L, "admin@mail.com", true))
                    .isInstanceOf(ReservaNoCancelableException.class);
        }

        @Test
        void noSePuedeCancelarUnaReservaCompletada() {
            Reserva reserva = reservaDeAnaQueEmpiezaEn(Duration.ofDays(1));
            ReflectionTestUtils.setField(reserva, "estado", EstadoReserva.COMPLETADA);

            assertThatThrownBy(() -> service.cancelar(7L, "admin@mail.com", true))
                    .isInstanceOf(ReservaNoCancelableException.class);
        }

        @Test
        void reservaInexistenteLanza404() {
            when(reservaRepository.findConDetalleById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelar(99L, ANA, false))
                    .isInstanceOf(ReservaNoEncontradaException.class);
        }
    }

    /** Los correos dependen de estos eventos: se comprueba qué se publica y cuándo no. */
    @Nested
    class Eventos {

        private Reserva reservaDeAnaManana() {
            Reserva reserva = new Reserva(ana, sala, instante(MANANA.atTime(10, 0)), instante(MANANA.atTime(11, 0)));
            ReflectionTestUtils.setField(reserva, "id", 7L);
            when(reservaRepository.findConDetalleById(7L)).thenReturn(Optional.of(reserva));
            return reserva;
        }

        @Test
        void crearPublicaReservaCreadaEvent() {
            when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> {
                Reserva reserva = inv.getArgument(0);
                ReflectionTestUtils.setField(reserva, "id", 42L);
                return reserva;
            });

            service.crear(pedido(MANANA.atTime(10, 0), MANANA.atTime(11, 0)), ANA);

            verify(eventos).publishEvent(new ReservaCreadaEvent(42L));
        }

        @Test
        void unaReservaRechazadaNoPublicaNada() {
            when(reservaRepository.existeSolapamiento(any(), any(), any())).thenReturn(true);

            assertThatThrownBy(() -> service.crear(pedido(MANANA.atTime(10, 0), MANANA.atTime(11, 0)), ANA))
                    .isInstanceOf(ConflictoReservaException.class);
            verify(eventos, never()).publishEvent(any(Object.class));
        }

        @Test
        void elClienteQueCancelaLaSuyaNoEsPorAdministrador() {
            reservaDeAnaManana();

            service.cancelar(7L, ANA, false);

            verify(eventos).publishEvent(new ReservaCanceladaEvent(7L, false));
        }

        @Test
        void unAdminQueCancelaUnaAjenaEsPorAdministrador() {
            reservaDeAnaManana();

            service.cancelar(7L, "admin@mail.com", true);

            verify(eventos).publishEvent(new ReservaCanceladaEvent(7L, true));
        }

        @Test
        void unaCancelacionRechazadaNoPublicaNada() {
            reservaDeAnaManana().cancelar();

            assertThatThrownBy(() -> service.cancelar(7L, ANA, false))
                    .isInstanceOf(ReservaNoCancelableException.class);
            verify(eventos, never()).publishEvent(any(Object.class));
        }
    }

    /** La misma regla que cancelar(), usada por las páginas para mostrar u ocultar el botón. */
    @Nested
    class EsCancelable {

        private ReservaResponse reservaQueEmpiezaEn(Duration dentroDe, EstadoReserva estado) {
            LocalDateTime inicio = AHORA.plus(dentroDe);
            return new ReservaResponse(7L, null, null, inicio, inicio.plusHours(1), estado, Instant.now(), null);
        }

        @ParameterizedTest(name = "faltan {0} min, admin={1} → {2}")
        @CsvSource({
                "1440, false, true",   // mañana: el cliente puede
                "120,  false, true",   // justo 2 h antes: el cliente puede
                "119,  false, false",  // menos de 2 h: el cliente ya no
                "30,   true,  true",   // el admin no tiene plazo
                "0,    true,  false",  // ya empezó: nadie
                "-60,  true,  false"   // ya pasó: nadie
        })
        void respetaElPlazoYElRol(long minutos, boolean esAdmin, boolean esperado) {
            ReservaResponse reserva = reservaQueEmpiezaEn(Duration.ofMinutes(minutos), EstadoReserva.CONFIRMADA);

            assertThat(service.esCancelable(reserva, esAdmin)).isEqualTo(esperado);
        }

        @Test
        void unaReservaCanceladaNoEsCancelable() {
            ReservaResponse reserva = reservaQueEmpiezaEn(Duration.ofDays(1), EstadoReserva.CANCELADA);

            assertThat(service.esCancelable(reserva, true)).isFalse();
        }
    }

    @Nested
    class Consultar {

        @Test
        void clienteNoPuedeVerUnaReservaAjena() {
            Reserva reserva = new Reserva(ana, sala, instante(MANANA.atTime(10, 0)),
                    instante(MANANA.atTime(11, 0)));
            when(reservaRepository.findConDetalleById(7L)).thenReturn(Optional.of(reserva));

            assertThatThrownBy(() -> service.obtener(7L, "beto@mail.com", false))
                    .isInstanceOf(ReservaNoEncontradaException.class);
            assertThat(service.obtener(7L, ANA, false).recurso().nombre()).isEqualTo("Sala Andes");
            assertThat(service.obtener(7L, "admin@mail.com", true)).isNotNull();
        }

        @Test
        void listarMiasEntreBuscaDesdeElInicioDelPrimerDiaHastaElFinDelUltimoEnLaZonaDelNegocio() {
            Reserva reserva = new Reserva(ana, sala, instante(MANANA.atTime(10, 0)), instante(MANANA.atTime(11, 0)));
            // 16/11 00:00 en Guayaquil = 05:00 UTC; hasta es exclusivo: 18/11 00:00 Guayaquil = 18/11 05:00 UTC
            when(reservaRepository.findDeUsuarioEntre(ANA, Instant.parse("2026-11-16T05:00:00Z"),
                    Instant.parse("2026-11-18T05:00:00Z"))).thenReturn(List.of(reserva));

            List<ReservaResponse> reservas = service.listarMiasEntre("  ANA@mail.com ", HOY, MANANA);

            assertThat(reservas).singleElement()
                    .satisfies(r -> assertThat(r.fechaInicio()).isEqualTo(MANANA.atTime(10, 0)));
        }

        @Test
        void listarMiasEntreConDesdePosteriorAHastaEsInvalido() {
            assertThatThrownBy(() -> service.listarMiasEntre(ANA, MANANA, HOY))
                    .isInstanceOf(FiltroInvalidoException.class);
        }

        @Test
        void proximaBuscaLaPrimeraConfirmadaQueAunNoEmpieza() {
            Reserva reserva = new Reserva(ana, sala, instante(MANANA.atTime(10, 0)), instante(MANANA.atTime(11, 0)));
            when(reservaRepository.findFirstByUsuarioEmailAndEstadoAndFechaInicioAfterOrderByFechaInicioAsc(
                    ANA, EstadoReserva.CONFIRMADA, instante(AHORA))).thenReturn(Optional.of(reserva));

            assertThat(service.proxima(ANA)).hasValueSatisfying(
                    r -> assertThat(r.recurso().nombre()).isEqualTo("Sala Andes"));
            assertThat(service.proxima("beto@mail.com")).isEmpty();
        }

        @Test
        void filtroAdminConDesdePosteriorAHastaEsInvalido() {
            assertThatThrownBy(() -> service.listarTodas(null, MANANA, HOY, Pageable.unpaged()))
                    .isInstanceOf(FiltroInvalidoException.class);
        }
    }

    @Nested
    class Modificar {

        private static final long ID = 5L;
        private Recurso sala2;

        @BeforeEach
        void setUpModificar() {
            ReflectionTestUtils.setField(sala, "id", 1L);
            sala2 = Recurso.builder().nombre("Sala Pacífico").tipo(TipoRecurso.SALA_REUNIONES).capacidad(6)
                    .activo(true)
                    .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build();
            ReflectionTestUtils.setField(sala2, "id", 2L);
            when(recursoRepository.findById(2L)).thenReturn(Optional.of(sala2));
        }

        private Reserva reservaDeAna(LocalDateTime inicio, LocalDateTime fin) {
            Reserva reserva = new Reserva(ana, sala, instante(inicio), instante(fin));
            ReflectionTestUtils.setField(reserva, "id", ID);
            when(reservaRepository.findConDetalleById(ID)).thenReturn(Optional.of(reserva));
            return reserva;
        }

        private Reserva reservaDeAnaManana() {
            return reservaDeAna(MANANA.atTime(10, 0), MANANA.atTime(11, 0));
        }

        private static ModificarReservaRequest cambio(long recursoId, LocalDateTime inicio, LocalDateTime fin) {
            return new ModificarReservaRequest(recursoId, inicio, fin);
        }

        @Test
        void modificaEspacioDiaYHorario() {
            Reserva reserva = reservaDeAnaManana();
            LocalDateTime nuevoInicio = MANANA.plusDays(1).atTime(15, 0);

            ReservaResponse respuesta = service.modificar(ID,
                    cambio(2L, nuevoInicio, MANANA.plusDays(1).atTime(16, 30)), ANA, false);

            assertThat(respuesta.recurso().nombre()).isEqualTo("Sala Pacífico");
            assertThat(respuesta.fechaInicio()).isEqualTo(nuevoInicio);
            assertThat(respuesta.fechaFin()).isEqualTo(MANANA.plusDays(1).atTime(16, 30));
            assertThat(respuesta.id()).isEqualTo(ID);
            verify(reservaRepository).saveAndFlush(reserva);
            verify(reservaRepository).liberarRecordatorio(ID);
        }

        @Test
        void publicaEventoConLosDatosAnteriores() {
            reservaDeAnaManana();

            service.modificar(ID, cambio(2L, MANANA.atTime(15, 0), MANANA.atTime(16, 0)), ANA, false);

            ArgumentCaptor<ReservaModificadaEvent> captor = ArgumentCaptor.forClass(ReservaModificadaEvent.class);
            verify(eventos).publishEvent(captor.capture());
            ReservaModificadaEvent evento = captor.getValue();
            assertThat(evento.reservaId()).isEqualTo(ID);
            assertThat(evento.recursoAnterior()).isEqualTo("Sala Andes");
            assertThat(evento.inicioAnterior()).isEqualTo(MANANA.atTime(10, 0));
            assertThat(evento.finAnterior()).isEqualTo(MANANA.atTime(11, 0));
            assertThat(evento.porAdministrador()).isFalse();
        }

        @Test
        void sinCambiosNoPublicaEventoNiReiniciaRecordatorio() {
            reservaDeAnaManana();

            service.modificar(ID, cambio(1L, MANANA.atTime(10, 0), MANANA.atTime(11, 0)), ANA, false);

            verify(eventos, never()).publishEvent(any());
            verify(reservaRepository, never()).liberarRecordatorio(any());
            verify(reservaRepository, never()).saveAndFlush(any());
        }

        @Test
        void usaLaConsultaQueExcluyeLaPropiaReserva() {
            reservaDeAnaManana();
            when(reservaRepository.existeSolapamientoExcluyendo(1L, instante(MANANA.atTime(10, 30)),
                    instante(MANANA.atTime(11, 30)), ID)).thenReturn(true);

            assertThatThrownBy(() -> service.modificar(ID,
                    cambio(1L, MANANA.atTime(10, 30), MANANA.atTime(11, 30)), ANA, false))
                    .isInstanceOf(ConflictoReservaException.class);
            verify(reservaRepository, never()).existeSolapamiento(any(), any(), any());
            verify(reservaRepository, never()).saveAndFlush(any());
        }

        @Test
        void reservaDeUnaSerieLanza409() {
            Reserva reserva = new Reserva(ana, sala, instante(MANANA.atTime(10, 0)), instante(MANANA.atTime(11, 0)),
                    new SerieReserva(ana, sala, 4));
            when(reservaRepository.findConDetalleById(ID)).thenReturn(Optional.of(reserva));

            assertThatThrownBy(() -> service.modificar(ID,
                    cambio(1L, MANANA.atTime(12, 0), MANANA.atTime(13, 0)), ANA, false))
                    .isInstanceOf(ReservaNoModificableException.class)
                    .hasMessage("Las reservas semanales no se pueden modificar");
        }

        @Test
        void canceladaLanza409() {
            reservaDeAnaManana().cancelar();

            assertThatThrownBy(() -> service.modificar(ID,
                    cambio(1L, MANANA.atTime(12, 0), MANANA.atTime(13, 0)), ANA, false))
                    .isInstanceOf(ReservaNoModificableException.class)
                    .hasMessage("La reserva ya está cancelada");
        }

        @Test
        void yaEmpezadaLanza409() {
            reservaDeAna(AHORA.minusMinutes(30), AHORA.plusMinutes(30));

            assertThatThrownBy(() -> service.modificar(ID,
                    cambio(1L, MANANA.atTime(12, 0), MANANA.atTime(13, 0)), "admin@mail.com", true))
                    .isInstanceOf(ReservaNoModificableException.class)
                    .hasMessage("La reserva ya comenzó o finalizó");
        }

        @Test
        void clienteDentroDeLas2HorasLanzaFueraDePlazo() {
            reservaDeAna(HOY.atTime(10, 30), HOY.atTime(11, 30));

            assertThatThrownBy(() -> service.modificar(ID,
                    cambio(1L, MANANA.atTime(12, 0), MANANA.atTime(13, 0)), ANA, false))
                    .isInstanceOf(ModificacionFueraDePlazoException.class)
                    .hasMessage("Solo se puede modificar hasta 2 horas antes del inicio");
        }

        @Test
        void adminDentroDeLas2HorasPuedeModificar() {
            reservaDeAna(HOY.atTime(10, 30), HOY.atTime(11, 30));

            service.modificar(ID, cambio(1L, MANANA.atTime(12, 0), MANANA.atTime(13, 0)), "admin@mail.com", true);

            ArgumentCaptor<ReservaModificadaEvent> captor = ArgumentCaptor.forClass(ReservaModificadaEvent.class);
            verify(eventos).publishEvent(captor.capture());
            assertThat(captor.getValue().porAdministrador()).isTrue();
        }

        @Test
        void reservaAjenaLanza404() {
            reservaDeAnaManana();

            assertThatThrownBy(() -> service.modificar(ID,
                    cambio(1L, MANANA.atTime(12, 0), MANANA.atTime(13, 0)), "beto@mail.com", false))
                    .isInstanceOf(ReservaNoEncontradaException.class);
        }

        @Test
        void recursoDestinoInactivoLanza404() {
            reservaDeAnaManana();
            ReflectionTestUtils.setField(sala2, "activo", false);

            assertThatThrownBy(() -> service.modificar(ID,
                    cambio(2L, MANANA.atTime(12, 0), MANANA.atTime(13, 0)), ANA, false))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        void nuevoHorarioEnElPasadoLanza400() {
            reservaDeAnaManana();

            assertThatThrownBy(() -> service.modificar(ID,
                    cambio(1L, HOY.atTime(8, 0), HOY.atTime(8, 30)), ANA, false))
                    .isInstanceOf(ReservaInvalidaException.class);
        }

        @Test
        void nuevoHorarioFueraDeAtencionLanza400() {
            reservaDeAnaManana();

            assertThatThrownBy(() -> service.modificar(ID,
                    cambio(1L, MANANA.atTime(19, 30), MANANA.atTime(20, 30)), ANA, false))
                    .isInstanceOf(ReservaInvalidaException.class);
        }

        @Test
        void carreraContraElExcludeSeTraduceAConflicto() {
            reservaDeAnaManana();
            when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(new DataIntegrityViolationException(
                    "exclusion", new SQLException("conflicto", "23P01")));

            assertThatThrownBy(() -> service.modificar(ID,
                    cambio(1L, MANANA.atTime(14, 0), MANANA.atTime(15, 0)), ANA, false))
                    .isInstanceOf(ConflictoReservaException.class);
            verify(eventos, never()).publishEvent(any());
        }

        @Test
        void deadlockContraElExcludeSeTraduceAConflicto() {
            // Con escrituras simultáneas sobre el EXCLUDE (GiST), PostgreSQL puede abortar con 40P01
            // (deadlock) en vez de 23P01: para el usuario es lo mismo, el hueco ya no está libre.
            reservaDeAnaManana();
            when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(new CannotAcquireLockException(
                    "deadlock", new SQLException("deadlock detected", "40P01")));

            assertThatThrownBy(() -> service.modificar(ID,
                    cambio(1L, MANANA.atTime(14, 0), MANANA.atTime(15, 0)), ANA, false))
                    .isInstanceOf(ConflictoReservaException.class);
        }

        @Test
        void esModificableFalsoParaSerieYVerdaderoParaIndividual() {
            ReservaResponse.RecursoResumen recurso = new ReservaResponse.RecursoResumen(1L, "Sala Andes");
            ReservaResponse individual = new ReservaResponse(ID, recurso, null, MANANA.atTime(10, 0),
                    MANANA.atTime(11, 0), EstadoReserva.CONFIRMADA, null, null);
            ReservaResponse deSerie = new ReservaResponse(6L, recurso, null, MANANA.atTime(10, 0),
                    MANANA.atTime(11, 0), EstadoReserva.CONFIRMADA, null, 3L);

            assertThat(service.esModificable(individual, false)).isTrue();
            assertThat(service.esModificable(deSerie, false)).isFalse();
        }
    }
}
