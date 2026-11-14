package com.ramses.reservas.common.exception;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo existe en tests: provoca un error inesperado para verificar la respuesta 500. */
@RestController
class ErroresTestController {

    static final String RUTA_ERROR = "/api/v1/test/error-inesperado";
    static final String MENSAJE_INTERNO = "detalle interno que nunca debe llegar al cliente";

    @GetMapping(RUTA_ERROR)
    String fallar() {
        throw new IllegalStateException(MENSAJE_INTERNO);
    }
}
