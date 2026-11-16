package com.ramses.reservas.reserva;

import static com.ramses.reservas.common.util.SeguridadUtils.esAdmin;

import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.reserva.dto.CrearReservaRequest;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** authentication.getName() es el "subject" del JWT, es decir, el email del usuario. */
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas", description = "Crear, consultar y cancelar reservas del usuario autenticado")
@SecurityRequirement(name = "bearerAuth")
public class ReservaController {

    private final ReservaService reservaService;

    @PostMapping
    @Operation(summary = "Crear una reserva (fechas en hora local del coworking)")
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody CrearReservaRequest request,
                                                 @Parameter(hidden = true) Authentication authentication) {
        ReservaResponse creada = reservaService.crear(request, authentication.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @GetMapping("/mias")
    @Operation(summary = "Mis reservas (paginado, más recientes primero)")
    public PaginaResponse<ReservaResponse> mias(
            @RequestParam(required = false) EstadoReserva estado,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaInicio", direction = Sort.Direction.DESC)
            Pageable pageable,
            @Parameter(hidden = true) Authentication authentication) {
        return reservaService.listarMias(authentication.getName(), estado, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una reserva propia (ADMIN: cualquiera)")
    public ReservaResponse obtener(@PathVariable Long id, @Parameter(hidden = true) Authentication authentication) {
        return reservaService.obtener(id, authentication.getName(), esAdmin(authentication));
    }

    /** PATCH (no DELETE): la reserva no se borra, cambia de estado y queda en el historial. */
    @PatchMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar una reserva (cliente: propia y hasta 2 h antes; ADMIN: cualquiera)")
    public ReservaResponse cancelar(@PathVariable Long id, @Parameter(hidden = true) Authentication authentication) {
        return reservaService.cancelar(id, authentication.getName(), esAdmin(authentication));
    }
}
