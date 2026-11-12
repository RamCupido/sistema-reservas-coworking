package com.ramses.reservas.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades "jwt.*" tipadas. Al ser un record, se validan una sola vez al arrancar:
 * si la clave es demasiado corta, la app no levanta (fail-fast).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration expiracion) {

    /** HS256 exige una clave de al menos 256 bits. */
    static final int BYTES_MINIMOS_SECRET = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < BYTES_MINIMOS_SECRET) {
            throw new IllegalArgumentException(
                    "jwt.secret (JWT_SECRET) debe tener al menos " + BYTES_MINIMOS_SECRET + " bytes");
        }
        if (expiracion == null) {
            expiracion = Duration.ofHours(1);
        }
    }
}
