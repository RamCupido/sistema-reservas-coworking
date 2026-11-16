package com.ramses.reservas.reserva;

import com.ramses.reservas.common.exception.RecursoNoEncontradoException;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.reserva.dto.DisponibilidadResponse;
import com.ramses.reservas.reserva.dto.DisponibilidadResponse.Bloque;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisponibilidadService {

    private static final Duration DURACION_BLOQUE = Duration.ofMinutes(30);

    private final RecursoRepository recursoRepository;
    private final ReservaRepository reservaRepository;
    private final ReservasProperties reservasProperties;
    private final Clock clock;

    /**
     * Divide el horario de atención en bloques de 30 min. Un bloque está disponible si no
     * se solapa con ninguna reserva no cancelada y todavía no ha empezado.
     * Una sola consulta trae las reservas del día; el cruce se hace en memoria.
     */
    @Transactional(readOnly = true)
    public DisponibilidadResponse calcular(Long recursoId, LocalDate fecha) {
        Recurso recurso = recursoRepository.findById(recursoId)
                .filter(Recurso::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException(recursoId));

        ZoneId zona = reservasProperties.zonaHoraria();
        Instant apertura = fecha.atTime(recurso.getHoraApertura()).atZone(zona).toInstant();
        Instant cierre = fecha.atTime(recurso.getHoraCierre()).atZone(zona).toInstant();
        List<Reserva> ocupadas = reservaRepository.findNoCanceladasEnRango(recursoId, apertura, cierre);
        Instant ahora = Instant.now(clock);

        List<Bloque> bloques = new ArrayList<>();
        for (LocalTime hora = recurso.getHoraApertura();
             hora.isBefore(recurso.getHoraCierre());
             hora = hora.plus(DURACION_BLOQUE)) {

            Instant inicioBloque = fecha.atTime(hora).atZone(zona).toInstant();
            Instant finBloque = inicioBloque.plus(DURACION_BLOQUE);
            boolean ocupado = ocupadas.stream().anyMatch(r ->
                    r.getFechaInicio().isBefore(finBloque) && inicioBloque.isBefore(r.getFechaFin()));
            boolean yaEmpezo = !inicioBloque.isAfter(ahora);

            bloques.add(new Bloque(hora, hora.plus(DURACION_BLOQUE), !ocupado && !yaEmpezo));
        }
        return new DisponibilidadResponse(recursoId, fecha, recurso.getHoraApertura(), recurso.getHoraCierre(),
                bloques);
    }
}
