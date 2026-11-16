package com.ramses.reservas.reserva;

import com.ramses.reservas.reserva.dto.DisponibilidadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vive en el paquete reserva aunque la URL cuelgue de /recursos: así "recurso" no
 * depende de "reserva" (la dependencia va en un solo sentido). Es pública (GET /recursos/**).
 */
@RestController
@RequestMapping("/api/v1/recursos")
@RequiredArgsConstructor
@Tag(name = "Recursos")
public class DisponibilidadController {

    private final DisponibilidadService disponibilidadService;

    @GetMapping("/{id}/disponibilidad")
    @Operation(summary = "Bloques de 30 min libres/ocupados de un recurso en un día")
    public DisponibilidadResponse disponibilidad(
            @PathVariable Long id,
            @Parameter(example = "2026-11-20")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return disponibilidadService.calcular(id, fecha);
    }
}
