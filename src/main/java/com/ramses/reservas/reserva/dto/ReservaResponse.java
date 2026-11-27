package com.ramses.reservas.reserva.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ramses.reservas.reserva.EstadoReserva;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * fechaInicio/fechaFin en hora local del coworking, mismo formato que la entrada.
 * serieId es null si la reserva es suelta, o el id de su serie semanal.
 */
public record ReservaResponse(
        Long id,
        RecursoResumen recurso,
        UsuarioResumen usuario,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") @Schema(type = "string", example = "2026-11-20T10:00")
        LocalDateTime fechaInicio,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") @Schema(type = "string", example = "2026-11-20T11:30")
        LocalDateTime fechaFin,
        EstadoReserva estado,
        Instant fechaCreacion,
        @Schema(nullable = true, example = "3")
        Long serieId) {

    public record RecursoResumen(Long id, String nombre) {
    }

    public record UsuarioResumen(Long id, String nombre, String email) {
    }
}
