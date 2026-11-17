package com.ramses.reservas.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity // habilita @PreAuthorize("hasRole('ADMIN')") en controllers/services
@RequiredArgsConstructor
public class SecurityConfig {

    private final ProblemDetailSecurityHandler problemDetailSecurityHandler;

    /**
     * Cadena 1 — API REST: sin estado, autenticada con JWT.
     * securityMatcher hace que esta cadena solo atienda estas rutas; el resto pasa a la cadena web.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**")
                // CSRF protege contra cookies enviadas automáticamente por el navegador.
                // La API usa el header Authorization (no cookies), así que no aplica.
                .csrf(AbstractHttpConfigurer::disable)
                // Sin HttpSession: cada petición se autentica solo con su JWT.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/registro", "/api/v1/auth/login").permitAll()
                        // Catálogo público; la escritura se protege con @PreAuthorize en el controller.
                        .requestMatchers(HttpMethod.GET, "/api/v1/recursos", "/api/v1/recursos/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                // Resource Server: Spring lee "Authorization: Bearer ...", valida el JWT
                // con nuestro JwtDecoder y crea la Authentication. No hace falta un filtro propio.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemDetailSecurityHandler)
                        .accessDeniedHandler(problemDetailSecurityHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemDetailSecurityHandler)
                        .accessDeniedHandler(problemDetailSecurityHandler));
        return http.build();
    }

    /**
     * Cadena 2 — páginas Thymeleaf: sesión con cookie HttpOnly y login por formulario.
     * Aquí CSRF va ACTIVADO: el navegador envía la cookie de sesión automáticamente, así que
     * otra web podría provocar un POST en nombre del usuario. Thymeleaf añade el token
     * oculto en cada formulario con th:action.
     */
    @Bean
    @Order(2)
    SecurityFilterChain webSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/recursos/**").permitAll()
                        .requestMatchers("/registro").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        // false: tras el login vuelve a la página protegida que se pidió (si la hubo)
                        .defaultSuccessUrl("/", false)
                        .failureUrl("/login?error")
                        .permitAll())
                // Logout solo por POST (con token CSRF): un <img src="/logout"> no puede cerrar la sesión.
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout")
                        .permitAll());
        return http.build();
    }

    /** Convierte el claim "rol": "ADMIN" del token en la authority "ROLE_ADMIN". */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtConfig.CLAIM_ROL);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** BCrypt: hash lento y con salt aleatorio; ideal para contraseñas. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** AuthenticationManager que Spring arma con nuestro UserDetailsService + PasswordEncoder. */
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
