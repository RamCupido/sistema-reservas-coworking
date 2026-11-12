package com.ramses.reservas.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminInicializadorTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private AdminInicializador inicializador(String email, String password) {
        return new AdminInicializador(
                new AdminProperties(email, password, "Administrador"), usuarioRepository, passwordEncoder);
    }

    @Test
    void creaElAdminCuandoEstaConfiguradoYNoExiste() {
        when(usuarioRepository.existsByEmail("admin@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("clave-segura")).thenReturn("hash");

        inicializador(" Admin@Mail.com ", "clave-segura").inicializar();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@mail.com");
        assertThat(captor.getValue().getRol()).isEqualTo(Rol.ADMIN);
        assertThat(captor.getValue().getPassword()).isEqualTo("hash");
    }

    @Test
    void noHaceNadaSiElAdminYaExiste() {
        when(usuarioRepository.existsByEmail("admin@mail.com")).thenReturn(true);

        inicializador("admin@mail.com", "clave-segura").inicializar();

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void noHaceNadaSiNoEstaConfigurado() {
        inicializador("", "").inicializar();

        verify(usuarioRepository, never()).existsByEmail(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void fallaSiLaPasswordEsDemasiadoCorta() {
        when(usuarioRepository.existsByEmail("admin@mail.com")).thenReturn(false);

        assertThatThrownBy(() -> inicializador("admin@mail.com", "corta").inicializar())
                .isInstanceOf(IllegalStateException.class);
        verify(usuarioRepository, never()).save(any());
    }
}
