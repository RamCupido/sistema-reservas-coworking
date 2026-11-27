package com.ramses.reservas.reserva;

/** Se confirmó una serie semanal: se envía UN correo con todas sus fechas (no uno por reserva). */
public record SerieCreadaEvent(Long serieId) {
}
