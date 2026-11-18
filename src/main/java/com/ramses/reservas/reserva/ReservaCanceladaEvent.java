package com.ramses.reservas.reserva;

/** @param porAdministrador true si la canceló un ADMIN y no su dueño (el correo lo explica). */
public record ReservaCanceladaEvent(Long reservaId, boolean porAdministrador) {
}
