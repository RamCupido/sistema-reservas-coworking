package com.ramses.reservas.reserva.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Disponibilidad de un recurso en un día, en bloques de 30 minutos. No revela quién reservó. */
public record DisponibilidadResponse(
        Long recursoId,
        LocalDate fecha,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "08:00") LocalTime horaApertura,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "20:00") LocalTime horaCierre,
        List<Bloque> bloques) {

    public record Bloque(
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "10:00") LocalTime inicio,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "10:30") LocalTime fin,
            boolean disponible) {
    }
}
