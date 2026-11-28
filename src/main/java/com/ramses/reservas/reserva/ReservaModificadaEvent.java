package com.ramses.reservas.reserva;

import java.time.LocalDateTime;

/**
 * Se publica al cambiar espacio u horario de una reserva. Lleva los datos anteriores (en hora
 * local) porque, tras el commit, la fila ya solo tiene los nuevos y el correo muestra el "antes".
 *
 * @param porAdministrador true si la modificó un ADMIN y no su dueño.
 */
public record ReservaModificadaEvent(Long reservaId, String recursoAnterior, LocalDateTime inicioAnterior,
                                     LocalDateTime finAnterior, boolean porAdministrador) {
}
