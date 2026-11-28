package com.ramses.reservas.reserva.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Nuevo espacio y horario de una reserva individual, en hora local del coworking.
 * Mismas validaciones de forma que {@link CrearReservaRequest}; las reglas las aplica el service.
 */
public record ModificarReservaRequest(
        @NotNull(message = "El recurso es obligatorio")
        @Schema(example = "1")
        Long recursoId,

        @NotNull(message = "La fecha de inicio es obligatoria")
        @Schema(type = "string", example = "2026-11-20T10:00")
        LocalDateTime fechaInicio,

        @NotNull(message = "La fecha de fin es obligatoria")
        @Schema(type = "string", example = "2026-11-20T11:30")
        LocalDateTime fechaFin) {
}
