package com.ramses.reservas.recurso.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ramses.reservas.recurso.TipoRecurso;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalTime;

/** Las horas se serializan como "08:00" (sin segundos), el mismo formato que acepta la entrada. */
public record RecursoResponse(
        Long id,
        String nombre,
        String descripcion,
        TipoRecurso tipo,
        Integer capacidad,
        BigDecimal precioPorHora,
        boolean activo,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "08:00") LocalTime horaApertura,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "20:00") LocalTime horaCierre) {
}
