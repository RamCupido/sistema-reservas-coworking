package com.ramses.reservas.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce excepciones a respuestas RFC 7807 (ProblemDetail).
 * <p>
 * Extender ResponseEntityExceptionHandler cubre los errores estándar de Spring MVC
 * (JSON mal formado, parámetro de tipo incorrecto, método no permitido, ruta inexistente...).
 * Spring elige siempre el handler más específico, así que el de {@link Exception}
 * solo atrapa lo que ningún otro reconoce.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- Validación de @Valid (400) ---

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

    // --- Excepciones de dominio ---

    @ExceptionHandler(EntidadNoEncontradaException.class)
    public ProblemDetail handleNoEncontrada(EntidadNoEncontradaException ex) {
        return problema(HttpStatus.NOT_FOUND, ex.getTitulo(), ex.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail handleConflicto(ConflictoException ex) {
        return problema(HttpStatus.CONFLICT, ex.getTitulo(), ex.getMessage());
    }

    /** Más específico que el de ConflictoException: añade qué fechas chocan, para que un cliente las muestre. */
    @ExceptionHandler(ConflictoSerieException.class)
    public ProblemDetail handleConflictoSerie(ConflictoSerieException ex) {
        ProblemDetail problem = problema(HttpStatus.CONFLICT, ex.getTitulo(), ex.getMessage());
        problem.setProperty("fechasEnConflicto", ex.getFechas());
        return problem;
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail handleReglaNegocio(ReglaNegocioException ex) {
        return problema(HttpStatus.BAD_REQUEST, ex.getTitulo(), ex.getMessage());
    }

    // --- Persistencia ---

    /** Red de seguridad: un constraint de la BD saltó sin que el service lo detectara antes (p. ej. carrera). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "Conflicto de datos",
                "La operación entra en conflicto con datos existentes");
    }

    /** @Version: otro usuario modificó el mismo registro entre la lectura y la escritura. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleBloqueoOptimista(ObjectOptimisticLockingFailureException ex) {
        return problema(HttpStatus.CONFLICT, "Modificación concurrente",
                "El registro fue modificado por otra operación. Vuelve a intentarlo");
    }

    /** ?sort=campoInexistente → 400 en lugar de 500. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handlePropiedadInvalida(PropertyReferenceException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Parámetro inválido",
                "No se puede ordenar por '" + ex.getPropertyName() + "'");
    }

    // --- Seguridad ---

    /** Login fallido. Mismo mensaje exista o no el email, para no revelar qué cuentas existen. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problema(HttpStatus.UNAUTHORIZED, "No autenticado", "Credenciales inválidas");
    }

    /**
     * @PreAuthorize lanza AccessDeniedException dentro del controller. Sin este handler,
     * el de Exception la convertiría en un 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccesoDenegado(AccessDeniedException ex) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permisos para acceder a este recurso");
    }

    // --- Último recurso (500) ---

    /** El detalle real va al log; al cliente nunca se le envían mensajes internos ni stacktraces. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleInesperado(Exception ex) {
        log.error("Error inesperado", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ha ocurrido un error inesperado. Inténtalo de nuevo más tarde");
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detalle);
        problem.setTitle(titulo);
        return problem;
    }
}
