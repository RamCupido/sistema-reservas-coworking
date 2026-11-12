package com.ramses.reservas.usuario;

import com.ramses.reservas.common.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el primer ADMIN al arrancar a partir de ADMIN_EMAIL y ADMIN_PASSWORD.
 * Es idempotente: si el usuario ya existe no hace nada, así que puede correr en cada arranque.
 */
@Component
@RequiredArgsConstructor
public class AdminInicializador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicializador.class);
    static final int LONGITUD_MINIMA_PASSWORD = 8;

    private final AdminProperties adminProperties;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        inicializar();
    }

    void inicializar() {
        if (!adminProperties.estaConfigurado()) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD no definidos: no se crea administrador inicial");
            return;
        }

        String email = EmailUtils.normalizar(adminProperties.email());
        if (usuarioRepository.existsByEmail(email)) {
            log.debug("El administrador inicial {} ya existe", email);
            return;
        }

        // Fail-fast: mejor que la app no arranque a crear un admin con una clave débil.
        if (adminProperties.password().length() < LONGITUD_MINIMA_PASSWORD) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD debe tener al menos " + LONGITUD_MINIMA_PASSWORD + " caracteres");
        }

        usuarioRepository.save(new Usuario(
                adminProperties.nombre(),
                email,
                passwordEncoder.encode(adminProperties.password()),
                Rol.ADMIN));
        log.info("Administrador inicial creado: {}", email);
    }
}
