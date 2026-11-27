package com.ramses.reservas.reserva;

import java.util.List;

/**
 * Se cancelaron las fechas restantes de una serie.
 *
 * @param reservaIds solo las que se cancelaron ahora (el correo no menciona las pasadas ni las ya canceladas)
 * @param porAdministrador true si las canceló un ADMIN y no su dueño
 */
public record SerieCanceladaEvent(Long serieId, List<Long> reservaIds, boolean porAdministrador) {

    public SerieCanceladaEvent {
        reservaIds = List.copyOf(reservaIds);
    }
}
