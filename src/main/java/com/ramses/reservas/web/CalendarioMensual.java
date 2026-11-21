package com.ramses.reservas.web;

import com.ramses.reservas.reserva.dto.ReservaResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cuadrícula de un mes para la vista de calendario: semanas de lunes a domingo, incluidos los días
 * de los meses vecinos que completan la primera y la última semana (como cualquier calendario).
 * <p>
 * Es un objeto de presentación: no tiene reglas de negocio, solo organiza las reservas por día
 * para que la plantilla Thymeleaf no tenga que calcular fechas.
 */
public record CalendarioMensual(YearMonth mes, List<Semana> semanas) {

    public record Semana(List<Dia> dias) {
    }

    /**
     * @param delMes false para los días de relleno del mes anterior/siguiente.
     * @param pasado el día ya terminó (se muestra atenuado).
     */
    public record Dia(LocalDate fecha, boolean delMes, boolean hoy, boolean pasado, List<ReservaResponse> reservas) {

        public boolean tieneReservas() {
            return !reservas.isEmpty();
        }
    }

    /** Primer día visible: el lunes de la semana del día 1. */
    public static LocalDate inicioCuadricula(YearMonth mes) {
        return mes.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Último día visible: el domingo de la semana del último día del mes. */
    public static LocalDate finCuadricula(YearMonth mes) {
        return mes.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    /**
     * @param reservas reservas cuyo inicio cae dentro de la cuadrícula (las demás se ignoran).
     */
    public static CalendarioMensual construir(YearMonth mes, LocalDate hoy, List<ReservaResponse> reservas) {
        Map<LocalDate, List<ReservaResponse>> porDia = reservas.stream()
                .collect(Collectors.groupingBy(r -> r.fechaInicio().toLocalDate()));

        List<Semana> semanas = new ArrayList<>();
        List<Dia> dias = new ArrayList<>();
        for (LocalDate dia = inicioCuadricula(mes); !dia.isAfter(finCuadricula(mes)); dia = dia.plusDays(1)) {
            dias.add(new Dia(dia, YearMonth.from(dia).equals(mes), dia.equals(hoy), dia.isBefore(hoy),
                    porDia.getOrDefault(dia, List.of())));
            if (dias.size() == 7) {
                semanas.add(new Semana(List.copyOf(dias)));
                dias.clear();
            }
        }
        return new CalendarioMensual(mes, List.copyOf(semanas));
    }

    /** Días del mes (sin relleno) que tienen reservas, en orden: la "agenda" bajo el calendario. */
    public List<Dia> diasConReservas() {
        return semanas.stream()
                .flatMap(semana -> semana.dias().stream())
                .filter(dia -> dia.delMes() && dia.tieneReservas())
                .toList();
    }

    public int totalReservasDelMes() {
        return diasConReservas().stream().mapToInt(dia -> dia.reservas().size()).sum();
    }
}
