package com.ramses.reservas.reserva.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Reserva semanal: la primera fecha y cuántas semanas se repite (misma hora, mismo día de la semana).
 * Las fechas, en hora local del coworking, igual que en {@link CrearReservaRequest}.
 */
public record CrearSerieRequest(
        @NotNull(message = "El recurso es obligatorio")
        @Schema(example = "1")
        Long recursoId,

        @NotNull(message = "La fecha de inicio es obligatoria")
        @Schema(type = "string", example = "2026-11-23T09:00", description = "Primera reserva de la serie")
        LocalDateTime fechaInicio,

        @NotNull(message = "La fecha de fin es obligatoria")
        @Schema(type = "string", example = "2026-11-23T10:00")
        LocalDateTime fechaFin,

        @NotNull(message = "Indica cuántas semanas se repite")
        @Min(value = 2, message = "Una reserva semanal dura entre 2 y 12 semanas")
        @Max(value = 12, message = "Una reserva semanal dura entre 2 y 12 semanas")
        @Schema(example = "8", minimum = "2", maximum = "12")
        Integer semanas) {
}
