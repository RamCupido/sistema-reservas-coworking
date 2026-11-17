package com.ramses.reservas.web;

import com.ramses.reservas.common.exception.EntidadNoEncontradaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Errores de las páginas Thymeleaf → página HTML de error (no JSON).
 * <p>
 * basePackages limita este advice a los controllers de {@code web}; HIGHEST_PRECEDENCE hace que
 * Spring lo consulte antes que el {@code GlobalExceptionHandler} de la API, que sigue atendiendo
 * a los @RestController. Las reglas de negocio "esperables" (conflicto, fuera de plazo...) no
 * llegan aquí: los controllers las atrapan y las muestran como alerta o error de formulario.
 */
@ControllerAdvice(basePackages = "com.ramses.reservas.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ExceptionHandler(EntidadNoEncontradaException.class)
    public ModelAndView noEncontrada(EntidadNoEncontradaException ex) {
        return pagina("error/404", HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage());
    }

    /** ?fecha=abc, ?tipo=PISCINA, falta un parámetro obligatorio... */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ModelAndView peticionInvalida(Exception ex) {
        return pagina("error", HttpStatus.BAD_REQUEST, "Petición inválida",
                "Alguno de los datos de la dirección no es válido");
    }

    /** Doble clic en "Cancelar" o dos admins tocando la misma reserva a la vez (@Version). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ModelAndView modificacionConcurrente(ObjectOptimisticLockingFailureException ex) {
        return pagina("error", HttpStatus.CONFLICT, "Modificación concurrente",
                "El registro fue modificado por otra operación. Vuelve a intentarlo");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView inesperado(Exception ex) {
        log.error("Error inesperado en una página web", ex);
        return pagina("error", HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ha ocurrido un error inesperado. Inténtalo de nuevo más tarde");
    }

    private static ModelAndView pagina(String vista, HttpStatus status, String titulo, String mensaje) {
        ModelAndView mav = new ModelAndView(vista, status);
        mav.addObject("status", status.value());
        mav.addObject("titulo", titulo);
        mav.addObject("mensaje", mensaje);
        return mav;
    }
}
