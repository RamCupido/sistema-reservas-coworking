package com.ramses.reservas.usuario;

import com.ramses.reservas.usuario.dto.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

    private final UsuarioService usuarioService;

    /** El "subject" del JWT es el email del usuario autenticado. */
    @GetMapping("/me")
    @Operation(summary = "Datos del usuario autenticado")
    public UsuarioResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return usuarioService.obtenerPorEmail(jwt.getSubject());
    }
}
