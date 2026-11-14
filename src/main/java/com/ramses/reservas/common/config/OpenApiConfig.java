package com.ramses.reservas.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la API para Swagger UI. El SecurityScheme "bearerAuth" añade el botón
 * "Authorize": pegas ahí el accessToken del login y Swagger lo envía en cada petición.
 * Los endpoints protegidos lo referencian con @SecurityRequirement(name = "bearerAuth").
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "API de Reservas — Coworking y Salas de Reuniones",
        version = "v1",
        description = "Gestión de recursos (salas, escritorios, oficinas) y sus reservas. "
                + "Para endpoints protegidos: haz login en /api/v1/auth/login y usa el botón Authorize."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
