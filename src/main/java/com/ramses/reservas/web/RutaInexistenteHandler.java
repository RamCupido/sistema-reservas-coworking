package com.ramses.reservas.web;

import com.ramses.reservas.common.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Una URL que no existe (/pagina-inventada) no tiene controller, así que ningún advice
 * "por paquete" la ve. Se decide aquí según la ruta:
 * <ul>
 *   <li>/api/** → ProblemDetail JSON, delegando en el handler de la API.</li>
 *   <li>resto → se relanza la MISMA excepción: Spring la trata como "no manejada", responde 404
 *       y Spring Boot muestra la plantilla {@code templates/error/404.html}.</li>
 * </ul>
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RutaInexistenteHandler {

    private final GlobalExceptionHandler globalExceptionHandler;

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> rutaInexistente(NoResourceFoundException ex, HttpServletRequest request,
                                                  WebRequest webRequest) throws Exception {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        if (ruta.startsWith("/api/")) {
            return globalExceptionHandler.handleException(ex, webRequest);
        }
        throw ex;
    }
}
