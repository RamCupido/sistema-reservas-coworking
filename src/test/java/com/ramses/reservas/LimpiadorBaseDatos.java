package com.ramses.reservas;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Deja la BD vacía entre tests de integración. TRUNCATE ... CASCADE respeta las claves
 * foráneas (no se puede borrar un usuario con reservas con deleteAll()) y es más rápido.
 */
@Component
@RequiredArgsConstructor
public class LimpiadorBaseDatos {

    private final JdbcTemplate jdbcTemplate;

    public void limpiar() {
        jdbcTemplate.execute("TRUNCATE TABLE reservas, series_reserva, recursos, usuarios RESTART IDENTITY CASCADE");
    }
}
