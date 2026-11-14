package com.ramses.reservas.recurso.dto;

import com.ramses.reservas.recurso.TipoRecurso;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Se usa para crear (POST) y reemplazar (PUT). Las reglas de forma van aquí con
 * Bean Validation; las de negocio (horario coherente, nombre único) en el service.
 */
public record RecursoRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        @Schema(example = "Sala Andes")
        String nombre,

        @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres")
        @Schema(example = "Sala con pantalla 4K y videoconferencia")
        String descripcion,

        @NotNull(message = "El tipo es obligatorio")
        TipoRecurso tipo,

        @NotNull(message = "La capacidad es obligatoria")
        @Positive(message = "La capacidad debe ser mayor que 0")
        @Max(value = 1000, message = "La capacidad no puede superar 1000 personas")
        @Schema(example = "8")
        Integer capacidad,

        @NotNull(message = "El precio por hora es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio por hora no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El precio admite como máximo 2 decimales")
        @Schema(example = "25.00")
        BigDecimal precioPorHora,

        @NotNull(message = "La hora de apertura es obligatoria")
        @Schema(type = "string", example = "08:00")
        LocalTime horaApertura,

        @NotNull(message = "La hora de cierre es obligatoria")
        @Schema(type = "string", example = "20:00")
        LocalTime horaCierre,

        @Schema(description = "Opcional. Al crear, por defecto true; en PUT, null mantiene el valor actual")
        Boolean activo) {
}
