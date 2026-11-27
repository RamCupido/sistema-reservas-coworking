package com.ramses.reservas.reserva;

import static com.ramses.reservas.common.util.SeguridadUtils.esAdmin;

import com.ramses.reservas.reserva.dto.CrearSerieRequest;
import com.ramses.reservas.reserva.dto.SerieResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas semanales", description = "La misma franja cada semana, de 2 a 12 semanas")
@SecurityRequirement(name = "bearerAuth")
public class SerieReservaController {

    private final SerieReservaService serieService;

    @PostMapping("/recurrentes")
    @Operation(summary = "Crear una reserva semanal (todo o nada: si una fecha choca, 409 con fechasEnConflicto)")
    public ResponseEntity<SerieResponse> crear(@Valid @RequestBody CrearSerieRequest request,
                                               @Parameter(hidden = true) Authentication authentication) {
        SerieResponse creada = serieService.crear(request, authentication.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/reservas/series/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @GetMapping("/series/{id}")
    @Operation(summary = "Detalle de una serie propia con todas sus reservas (ADMIN: cualquiera)")
    public SerieResponse obtener(@PathVariable Long id, @Parameter(hidden = true) Authentication authentication) {
        return serieService.obtener(id, authentication.getName(), esAdmin(authentication));
    }

    @PatchMapping("/series/{id}/cancelar")
    @Operation(summary = "Cancelar las fechas restantes de una serie (las que aún se pueden cancelar)")
    public SerieResponse cancelar(@PathVariable Long id, @Parameter(hidden = true) Authentication authentication) {
        return serieService.cancelarRestantes(id, authentication.getName(), esAdmin(authentication));
    }
}
