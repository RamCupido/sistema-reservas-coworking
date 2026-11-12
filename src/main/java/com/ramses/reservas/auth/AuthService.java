package com.ramses.reservas.auth;

import com.ramses.reservas.auth.dto.LoginRequest;
import com.ramses.reservas.auth.dto.RegistroRequest;
import com.ramses.reservas.auth.dto.TokenResponse;
import com.ramses.reservas.common.exception.EmailYaRegistradoException;
import com.ramses.reservas.common.util.EmailUtils;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioMapper;
import com.ramses.reservas.usuario.UsuarioRepository;
import com.ramses.reservas.usuario.dto.UsuarioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PREFIJO_ROL = "ROLE_";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /**
     * Sin @Transactional a propósito: saveAndFlush corre en su propia transacción y así
     * podemos capturar la violación del UNIQUE (dos registros simultáneos con el mismo email)
     * sin que la transacción externa quede marcada como rollback-only.
     */
    public UsuarioResponse registrar(RegistroRequest request) {
        String email = EmailUtils.normalizar(request.email());
        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailYaRegistradoException(email);
        }

        Usuario usuario = new Usuario(
                request.nombre().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Rol.CLIENTE);
        try {
            return UsuarioMapper.toResponse(usuarioRepository.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException e) {
            throw new EmailYaRegistradoException(email);
        }
    }

    /** Si las credenciales son incorrectas, authenticate() lanza BadCredentialsException (→ 401). */
    public TokenResponse login(LoginRequest request) {
        Authentication autenticacion = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        EmailUtils.normalizar(request.email()), request.password()));

        return jwtService.generarToken(autenticacion.getName(), extraerRol(autenticacion));
    }

    private static Rol extraerRol(Authentication autenticacion) {
        return autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(PREFIJO_ROL))
                .map(authority -> Rol.valueOf(authority.substring(PREFIJO_ROL.length())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El usuario autenticado no tiene rol"));
    }
}
