package com.ramses.reservas.reserva;

import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** @PreAuthorize a nivel de clase: aplica a todos los métodos, imposible olvidarlo en uno nuevo. */
@RestController
@RequestMapping("/api/v1/admin/reservas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administración")
@SecurityRequirement(name = "bearerAuth")
public class AdminReservaController {

    private final ReservaService reservaService;

    @GetMapping
    @Operation(summary = "Todas las reservas con filtros (ADMIN)")
    public PaginaResponse<ReservaResponse> listar(
            @RequestParam(required = false) EstadoReserva estado,
            @Parameter(description = "Día de inicio mínimo (inclusive)", example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Día de inicio máximo (inclusive)", example = "2026-11-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaInicio", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return reservaService.listarTodas(estado, desde, hasta, pageable);
    }
}
