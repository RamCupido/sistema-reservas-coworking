package com.ramses.reservas.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ramses.reservas.common.exception.RecursoNoEncontradoException;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.reserva.dto.DisponibilidadResponse;
import com.ramses.reservas.reserva.dto.DisponibilidadResponse.Bloque;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DisponibilidadServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 16);
    private static final LocalDate MANANA = HOY.plusDays(1);

    private final RecursoRepository recursoRepository = mock(RecursoRepository.class);
    private final ReservaRepository reservaRepository = mock(ReservaRepository.class);
    private DisponibilidadService service;
    private Recurso sala;

    @BeforeEach
    void setUp() {
        Clock ahora = Clock.fixed(HOY.atTime(10, 15).atZone(ZONA).toInstant(), ZONA); // hoy 10:15
        service = new DisponibilidadService(recursoRepository, reservaRepository,
                new ReservasProperties(ZONA, Duration.ofHours(2)), ahora);
        sala = Recurso.builder().nombre("Sala").tipo(TipoRecurso.SALA_REUNIONES).capacidad(4)
                .activo(true)
                .horaApertura(LocalTime.of(9, 0)).horaCierre(LocalTime.of(12, 0)).build();
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(sala));
    }

    private Reserva reserva(LocalDateTime inicio, LocalDateTime fin) {
        return new Reserva(new Usuario("Ana", "ana@mail.com", "x", Rol.CLIENTE), sala,
                inicio.atZone(ZONA).toInstant(), fin.atZone(ZONA).toInstant());
    }

    private static List<String> libres(DisponibilidadResponse respuesta) {
        return respuesta.bloques().stream().filter(Bloque::disponible).map(b -> b.inicio().toString()).toList();
    }

    @Test
    void diaLibreTieneTodosLosBloquesDisponibles() {
        DisponibilidadResponse respuesta = service.calcular(1L, MANANA);

        // 09:00-12:00 → 6 bloques de 30 min
        assertThat(respuesta.bloques()).hasSize(6);
        assertThat(respuesta.bloques().getFirst().inicio()).isEqualTo(LocalTime.of(9, 0));
        assertThat(respuesta.bloques().getLast().fin()).isEqualTo(LocalTime.of(12, 0));
        assertThat(libres(respuesta)).hasSize(6);
    }

    @Test
    void reservaIgnoradaApareceComoLibre() {
        Reserva propia = reserva(MANANA.atTime(10, 0), MANANA.atTime(11, 0));
        ReflectionTestUtils.setField(propia, "id", 7L);
        Reserva ajena = reserva(MANANA.atTime(11, 0), MANANA.atTime(11, 30));
        ReflectionTestUtils.setField(ajena, "id", 8L);
        when(reservaRepository.findNoCanceladasEnRango(eq(1L), any(), any())).thenReturn(List.of(propia, ajena));

        assertThat(libres(service.calcular(1L, MANANA, 7L)))
                .containsExactly("09:00", "09:30", "10:00", "10:30", "11:30");
        assertThat(libres(service.calcular(1L, MANANA))).containsExactly("09:00", "09:30", "11:30");
    }

    @Test
    void bloquesOcupadosPorReservasNoEstanDisponibles() {
        when(reservaRepository.findNoCanceladasEnRango(eq(1L), any(), any())).thenReturn(List.of(
                reserva(MANANA.atTime(9, 30), MANANA.atTime(10, 30)),
                reserva(MANANA.atTime(11, 30), MANANA.atTime(12, 0))));

        assertThat(libres(service.calcular(1L, MANANA))).containsExactly("09:00", "10:30", "11:00");
    }

    @Test
    void hoyLosBloquesQueYaEmpezaronNoEstanDisponibles() {
        // Ahora son las 10:15: 09:00, 09:30 y 10:00 ya empezaron
        assertThat(libres(service.calcular(1L, HOY))).containsExactly("10:30", "11:00", "11:30");
    }

    @Test
    void recursoInactivoLanza404() {
        sala.setActivo(false);

        assertThatThrownBy(() -> service.calcular(1L, MANANA)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
