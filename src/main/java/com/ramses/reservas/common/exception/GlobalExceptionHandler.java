package com.ramses.reservas.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce excepciones a respuestas RFC 7807 (ProblemDetail).
 * Extender ResponseEntityExceptionHandler hace que los errores estándar de Spring MVC
 * (JSON mal formado, método no permitido, etc.) también salgan como ProblemDetail.
 * Versión mínima: se amplía en la Fase 2.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ex.getBody();
        problem.setTitle("Datos inválidos");
        problem.setDetail("La petición contiene campos inválidos");
        problem.setProperty("errores", errores);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(EmailYaRegistradoException.class)
    public ProblemDetail handleEmailYaRegistrado(EmailYaRegistradoException ex) {
        return problema(HttpStatus.CONFLICT, "Email ya registrado", ex.getMessage());
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ProblemDetail handleUsuarioNoEncontrado(UsuarioNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Usuario no encontrado", ex.getMessage());
    }

    /** Login fallido. Mismo mensaje exista o no el email, para no revelar qué cuentas existen. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problema(HttpStatus.UNAUTHORIZED, "No autenticado", "Credenciales inválidas");
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detalle);
        problem.setTitle(titulo);
        return problem;
    }
}
