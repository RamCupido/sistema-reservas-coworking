package com.ramses.reservas.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ramses.reservas.auth.dto.LoginRequest;
import com.ramses.reservas.auth.dto.RegistroRequest;
import com.ramses.reservas.auth.dto.TokenResponse;
import com.ramses.reservas.common.exception.EmailYaRegistradoException;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import com.ramses.reservas.usuario.dto.UsuarioResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registrarGuardaClienteConEmailNormalizadoYPasswordHasheada() {
        when(usuarioRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("secreta123")).thenReturn("hash-bcrypt");
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse respuesta = authService.registrar(
                new RegistroRequest("  Ana  ", "  Ana@Mail.COM ", "secreta123"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(captor.capture());
        Usuario guardado = captor.getValue();
        assertThat(guardado.getNombre()).isEqualTo("Ana");
        assertThat(guardado.getEmail()).isEqualTo("ana@mail.com");
        assertThat(guardado.getPassword()).isEqualTo("hash-bcrypt");
        assertThat(guardado.getRol()).isEqualTo(Rol.CLIENTE);
        assertThat(respuesta.email()).isEqualTo("ana@mail.com");
    }

    @Test
    void registrarConEmailExistenteLanzaExcepcionYNoGuarda() {
        when(usuarioRepository.existsByEmail("ana@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(new RegistroRequest("Ana", "ana@mail.com", "secreta123")))
                .isInstanceOf(EmailYaRegistradoException.class);
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void registrarSimultaneoConMismoEmailSeTraduceAEmailYaRegistrado() {
        // Simula la carrera: el exists dio false, pero otro registro ganó y el UNIQUE de la BD salta.
        when(usuarioRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(usuarioRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_usuarios_email"));

        assertThatThrownBy(() -> authService.registrar(new RegistroRequest("Ana", "ana@mail.com", "secreta123")))
                .isInstanceOf(EmailYaRegistradoException.class);
    }

    @Test
    void loginCorrectoGeneraTokenConElRolDelUsuario() {
        var autenticado = UsernamePasswordAuthenticationToken.authenticated(
                "ana@mail.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(authenticationManager.authenticate(any())).thenReturn(autenticado);
        TokenResponse token = new TokenResponse("jwt", "Bearer", 3600);
        when(jwtService.generarToken("ana@mail.com", Rol.ADMIN)).thenReturn(token);

        assertThat(authService.login(new LoginRequest("Ana@Mail.com", "secreta123"))).isEqualTo(token);
    }

    @Test
    void loginIncorrectoPropagaBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ana@mail.com", "mala")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtService, never()).generarToken(any(), any());
    }
}
