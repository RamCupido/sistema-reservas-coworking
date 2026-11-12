package com.ramses.reservas.usuario;

import com.ramses.reservas.usuario.dto.UsuarioResponse;

public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    public static UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.getFechaCreacion());
    }
}
