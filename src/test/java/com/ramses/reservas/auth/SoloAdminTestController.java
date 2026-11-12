package com.ramses.reservas.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint que solo existe en el classpath de tests, para verificar la autorización
 * por rol sin exponer una ruta ficticia en producción.
 */
@RestController
class SoloAdminTestController {

    static final String RUTA = "/api/v1/test/solo-admin";

    @GetMapping(RUTA)
    @PreAuthorize("hasRole('ADMIN')")
    String soloAdmin() {
        return "ok";
    }
}
