package com.ramses.reservas.reserva;

import com.ramses.reservas.reserva.dto.ReservaResponse;
import com.ramses.reservas.reserva.dto.ReservaResponse.RecursoResumen;
import com.ramses.reservas.reserva.dto.ReservaResponse.UsuarioResumen;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Es un @Component (no una clase estática) porque necesita la zona horaria configurada. */
@Component
@RequiredArgsConstructor
public class ReservaMapper {

    private final ReservasProperties reservasProperties;

    public ReservaResponse toResponse(Reserva reserva) {
        return new ReservaResponse(
                reserva.getId(),
                new RecursoResumen(reserva.getRecurso().getId(), reserva.getRecurso().getNombre()),
                new UsuarioResumen(reserva.getUsuario().getId(), reserva.getUsuario().getNombre(),
                        reserva.getUsuario().getEmail()),
                aHoraLocal(reserva.getFechaInicio()),
                aHoraLocal(reserva.getFechaFin()),
                reserva.getEstado(),
                reserva.getFechaCreacion(),
                // getId() de un proxy LAZY no dispara ninguna consulta: el id ya está en la reserva.
                reserva.getSerie() == null ? null : reserva.getSerie().getId());
    }

    private LocalDateTime aHoraLocal(Instant instante) {
        return LocalDateTime.ofInstant(instante, reservasProperties.zonaHoraria());
    }
}
