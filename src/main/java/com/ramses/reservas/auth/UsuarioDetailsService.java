package com.ramses.reservas.auth;

import com.ramses.reservas.common.util.EmailUtils;
import com.ramses.reservas.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Puente entre nuestra tabla usuarios y Spring Security (lo usa el login). */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return usuarioRepository.findByEmail(EmailUtils.normalizar(email))
                .map(usuario -> User.withUsername(usuario.getEmail())
                        .password(usuario.getPassword())
                        .roles(usuario.getRol().name())   // se guarda como "ROLE_CLIENTE"/"ROLE_ADMIN"
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
