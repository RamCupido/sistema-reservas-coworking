package com.ramses.reservas.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ramses.reservas.reserva.EstadoReserva;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import com.ramses.reservas.reserva.dto.ReservaResponse.RecursoResumen;
import com.ramses.reservas.web.CalendarioMensual.Dia;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarioMensualTest {

    private static final YearMonth NOVIEMBRE = YearMonth.of(2026, 11);   // empieza en domingo, 30 días
    private static final LocalDate HOY = LocalDate.of(2026, 11, 16);

    private static ReservaResponse reserva(long id, LocalDateTime inicio) {
        return new ReservaResponse(id, new RecursoResumen(1L, "Sala"), null, inicio, inicio.plusHours(1),
                EstadoReserva.CONFIRMADA, Instant.now(), null);
    }

    private static List<Dia> todosLosDias(CalendarioMensual calendario) {
        return calendario.semanas().stream().flatMap(s -> s.dias().stream()).toList();
    }

    @Test
    void laCuadriculaVaDeLunesADomingoYCompletaLasSemanas() {
        // 1/11/2026 es domingo → la cuadrícula empieza el lunes 26/10; 30/11 es lunes → acaba el domingo 6/12.
        assertThat(CalendarioMensual.inicioCuadricula(NOVIEMBRE)).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(CalendarioMensual.finCuadricula(NOVIEMBRE)).isEqualTo(LocalDate.of(2026, 12, 6));

        CalendarioMensual calendario = CalendarioMensual.construir(NOVIEMBRE, HOY, List.of());

        assertThat(calendario.semanas()).hasSize(6).allSatisfy(s -> assertThat(s.dias()).hasSize(7));
        assertThat(calendario.semanas().getFirst().dias().getFirst().fecha().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void marcaLosDiasDeRellenoHoyYLosPasados() {
        List<Dia> dias = todosLosDias(CalendarioMensual.construir(NOVIEMBRE, HOY, List.of()));

        assertThat(dias.getFirst().delMes()).isFalse();                       // 26/10
        assertThat(dias.stream().filter(Dia::hoy)).singleElement()
                .satisfies(d -> assertThat(d.fecha()).isEqualTo(HOY));
        assertThat(dias.stream().filter(d -> d.fecha().equals(HOY.minusDays(1)))).allMatch(Dia::pasado);
        assertThat(dias.stream().filter(d -> d.fecha().equals(HOY))).noneMatch(Dia::pasado);
    }

    @Test
    void agrupaLasReservasPorDiaDeInicio() {
        CalendarioMensual calendario = CalendarioMensual.construir(NOVIEMBRE, HOY, List.of(
                reserva(1, LocalDateTime.of(2026, 11, 20, 10, 0)),
                reserva(2, LocalDateTime.of(2026, 11, 20, 15, 0)),
                reserva(3, LocalDateTime.of(2026, 11, 25, 9, 0))));

        assertThat(calendario.diasConReservas()).extracting(Dia::fecha)
                .containsExactly(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 25));
        assertThat(calendario.diasConReservas().getFirst().reservas()).extracting(ReservaResponse::id)
                .containsExactly(1L, 2L);
        assertThat(calendario.totalReservasDelMes()).isEqualTo(3);
    }

    @Test
    void lasReservasDeDiasDeRellenoSeVenEnLaCuadriculaPeroNoCuentanEnLaAgendaDelMes() {
        CalendarioMensual calendario = CalendarioMensual.construir(NOVIEMBRE, HOY,
                List.of(reserva(1, LocalDateTime.of(2026, 12, 2, 10, 0))));

        assertThat(todosLosDias(calendario)).filteredOn(Dia::tieneReservas).singleElement()
                .satisfies(d -> assertThat(d.delMes()).isFalse());
        assertThat(calendario.diasConReservas()).isEmpty();
        assertThat(calendario.totalReservasDelMes()).isZero();
    }
}
