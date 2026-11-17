package com.ramses.reservas.web.form;

import com.ramses.reservas.reserva.dto.CrearReservaRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** En la página se elige un día y dos horas; la API recibe dos LocalDateTime. */
@Getter
@Setter
public class ReservaForm {

    private Long recursoId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fecha;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaInicio;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaFin;

    public CrearReservaRequest toRequest() {
        return new CrearReservaRequest(recursoId, combinar(fecha, horaInicio), combinar(fecha, horaFin));
    }

    private static LocalDateTime combinar(LocalDate dia, LocalTime hora) {
        return dia == null || hora == null ? null : dia.atTime(hora);
    }
}
