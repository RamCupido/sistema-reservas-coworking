package com.ramses.reservas.usuario.dto;

import com.ramses.reservas.usuario.Rol;
import java.time.Instant;

/** Vista pública del usuario: nunca incluye la contraseña. */
public record UsuarioResponse(
        Long id,
        String nombre,
        String email,
        Rol rol,
        Instant fechaCreacion) {
}
