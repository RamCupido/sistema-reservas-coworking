package com.ramses.reservas.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Los errores 401/403 ocurren en los filtros de seguridad, antes de llegar a los
 * controllers, así que el @RestControllerAdvice no los ve. Este handler los responde
 * con el mismo formato ProblemDetail que el resto de la API.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 401: no hay token, o es inválido/expirado. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        String detalle = ex instanceof InvalidBearerTokenException
                ? "El token es inválido o ha expirado"
                : "Se requiere autenticación";
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escribir(response, request, HttpStatus.UNAUTHORIZED, "No autenticado", detalle);
    }

    /** 403: autenticado, pero sin el rol necesario. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        escribir(response, request, HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permisos para acceder a este recurso");
    }

    private void escribir(HttpServletResponse response, HttpServletRequest request,
                          HttpStatus status, String titulo, String detalle) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detalle);
        problem.setTitle(titulo);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
