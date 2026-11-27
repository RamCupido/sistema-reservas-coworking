package com.ramses.reservas.reserva.dto;

import com.ramses.reservas.reserva.dto.ReservaResponse.RecursoResumen;
import java.util.List;

/** Una serie semanal con todas sus reservas, en orden cronológico. */
public record SerieResponse(
        Long id,
        RecursoResumen recurso,
        int semanas,
        List<ReservaResponse> reservas) {
}
