package com.ramses.reservas.reserva;

/**
 * Evento de dominio: "se confirmó una reserva". ReservaService no sabe nada de correos;
 * quien quiera reaccionar (notificaciones, métricas...) escucha el evento.
 * Solo lleva el id: el listener vuelve a leer la reserva ya confirmada en la BD.
 */
public record ReservaCreadaEvent(Long reservaId) {
}
