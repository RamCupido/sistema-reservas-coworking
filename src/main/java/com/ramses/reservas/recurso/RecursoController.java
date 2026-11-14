package com.ramses.reservas.recurso;

import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.recurso.dto.RecursoRequest;
import com.ramses.reservas.recurso.dto.RecursoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/recursos")
@RequiredArgsConstructor
@Tag(name = "Recursos", description = "Catálogo de salas y espacios. Lectura pública; escritura solo ADMIN")
public class RecursoController {

    private final RecursoService recursoService;

    @GetMapping
    @Operation(summary = "Listar recursos (paginado, filtros opcionales)")
    public PaginaResponse<RecursoResponse> listar(
            @RequestParam(required = false) TipoRecurso tipo,
            @RequestParam(required = false) @Positive Integer capacidadMinima,
            @Parameter(description = "Solo ADMIN: incluye recursos desactivados")
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC)
            Pageable pageable,
            @Parameter(hidden = true) Authentication authentication) {
        return recursoService.listar(tipo, capacidadMinima, incluirInactivos && esAdmin(authentication), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un recurso por id")
    public RecursoResponse obtener(@PathVariable Long id, @Parameter(hidden = true) Authentication authentication) {
        return recursoService.obtener(id, esAdmin(authentication));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Crear un recurso (ADMIN)")
    public ResponseEntity<RecursoResponse> crear(@Valid @RequestBody RecursoRequest request) {
        RecursoResponse creado = recursoService.crear(request);
        // 201 Created + header Location con la URL del nuevo recurso (convención REST).
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Reemplazar un recurso (ADMIN)")
    public RecursoResponse actualizar(@PathVariable Long id, @Valid @RequestBody RecursoRequest request) {
        return recursoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desactivar un recurso (borrado lógico, ADMIN)")
    public void eliminar(@PathVariable Long id) {
        recursoService.desactivar(id);
    }

    /** En rutas públicas el usuario puede ser anónimo: entonces authentication es null. */
    private static boolean esAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
