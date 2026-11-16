package com.ramses.reservas.common.util;

import org.springframework.security.core.Authentication;

public final class SeguridadUtils {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private SeguridadUtils() {
    }

    /** En rutas públicas el usuario puede ser anónimo: entonces authentication es null. */
    public static boolean esAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }
}
