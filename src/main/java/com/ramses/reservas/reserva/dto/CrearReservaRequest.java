package com.ramses.reservas.reserva.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Fechas en hora local del coworking (sin zona): "2026-11-20T10:00".
 * Las reglas de negocio (horario, bloques de 30 min, pasado) las valida el service.
 * No hay campo de precio: se calcula siempre en el backend.
 */
public record CrearReservaRequest(
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
