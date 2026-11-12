package com.ramses.reservas.auth;

import com.ramses.reservas.auth.dto.TokenResponse;
import com.ramses.reservas.usuario.Rol;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    /** Se inyecta un Clock (y no Instant.now()) para poder probar tokens expirados. */
    private final Clock clock;

    public TokenResponse generarToken(String email, Rol rol) {
        Instant ahora = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(jwtProperties.expiracion()))
                .subject(email)
                .claim(JwtConfig.CLAIM_ROL, rol.name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", jwtProperties.expiracion().toSeconds());
    }
}
