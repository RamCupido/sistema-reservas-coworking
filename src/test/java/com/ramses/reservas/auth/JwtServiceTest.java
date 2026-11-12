package com.ramses.reservas.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ramses.reservas.auth.dto.TokenResponse;
import com.ramses.reservas.usuario.Rol;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/** Usa el encoder/decoder reales (sin Spring): así probamos la criptografía de verdad. */
class JwtServiceTest {

    private static final String SECRET = "clave-de-prueba-unitaria-de-32-bytes-o-mas";
    private static final Duration EXPIRACION = Duration.ofHours(1);

    private final JwtConfig jwtConfig = new JwtConfig();
    private final SecretKey clave = JwtConfig.claveSecreta(SECRET);
    private final JwtDecoder decoder = jwtConfig.jwtDecoder(clave);

    private JwtService jwtService(SecretKey claveFirma, Clock clock) {
        return new JwtService(jwtConfig.jwtEncoder(claveFirma), new JwtProperties(SECRET, EXPIRACION), clock);
    }

    @Test
    void generaUnTokenValidoConEmailYRol() {
        TokenResponse respuesta = jwtService(clave, Clock.systemUTC()).generarToken("ana@mail.com", Rol.ADMIN);

        Jwt jwt = decoder.decode(respuesta.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("ana@mail.com");
        assertThat(jwt.getClaimAsString(JwtConfig.CLAIM_ROL)).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtConfig.ISSUER);
        assertThat(respuesta.tokenType()).isEqualTo("Bearer");
        assertThat(respuesta.expiresIn()).isEqualTo(EXPIRACION.toSeconds());
    }

    @Test
    void rechazaUnTokenExpirado() {
        Clock haceDosHoras = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String token = jwtService(clave, haceDosHoras).generarToken("ana@mail.com", Rol.CLIENTE).accessToken();

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        SecretKey otraClave = JwtConfig.claveSecreta("otra-clave-distinta-tambien-de-32-bytes!!");
        String token = jwtService(otraClave, Clock.systemUTC()).generarToken("ana@mail.com", Rol.ADMIN).accessToken();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rechazaUnSecretDemasiadoCorto() {
        assertThatThrownBy(() -> new JwtProperties("corta", EXPIRACION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }
}
