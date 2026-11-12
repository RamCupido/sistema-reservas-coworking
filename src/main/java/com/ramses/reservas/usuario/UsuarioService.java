package com.ramses.reservas.usuario;

import com.ramses.reservas.common.exception.UsuarioNoEncontradoException;
import com.ramses.reservas.common.util.EmailUtils;
import com.ramses.reservas.usuario.dto.UsuarioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioResponse obtenerPorEmail(String email) {
        return usuarioRepository.findByEmail(EmailUtils.normalizar(email))
                .map(UsuarioMapper::toResponse)
                .orElseThrow(() -> new UsuarioNoEncontradoException(email));
    }
}
