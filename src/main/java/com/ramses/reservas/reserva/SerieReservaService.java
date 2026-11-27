package com.ramses.reservas.reserva;

import com.ramses.reservas.common.exception.ConflictoReservaException;
import com.ramses.reservas.common.exception.ConflictoSerieException;
import com.ramses.reservas.common.exception.RecursoNoEncontradoException;
import com.ramses.reservas.common.exception.ReservaInvalidaException;
import com.ramses.reservas.common.exception.ReservaNoCancelableException;
import com.ramses.reservas.common.exception.SerieNoEncontradaException;
import com.ramses.reservas.common.exception.UsuarioNoEncontradoException;
import com.ramses.reservas.common.util.EmailUtils;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.reserva.dto.CrearSerieRequest;
import com.ramses.reservas.reserva.dto.ReservaResponse.RecursoResumen;
import com.ramses.reservas.reserva.dto.SerieResponse;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reservas semanales: la misma franja, el mismo día de la semana, durante 2 a 12 semanas.
 * <p>
 * Cada semana es una {@link Reserva} normal ligada a una {@link SerieReserva}, así que el
 * constraint anti-solapamiento, los recordatorios y la cancelación individual no cambian.
 * La creación es <b>todo o nada</b>: si una sola fecha choca, no se crea ninguna.
 */
@Service
@RequiredArgsConstructor
public class SerieReservaService {

    private final SerieReservaRepository serieRepository;
    private final ReservaRepository reservaRepository;
    private final RecursoRepository recursoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReservaMapper reservaMapper;
    private final ReservasProperties reservasProperties;
    private final Clock clock;
    private final ApplicationEventPublisher eventos;

    /** Una semana de la serie, ya convertida a UTC. */
    record Ocurrencia(LocalDate dia, Instant inicio, Instant fin) {

        boolean seSolapaCon(Reserva otra) {
            return inicio.isBefore(otra.getFechaFin()) && otra.getFechaInicio().isBefore(fin);
        }
    }

    @Transactional
    public SerieResponse crear(CrearSerieRequest request, String emailUsuario) {
        int semanas = request.semanas();
        if (semanas < SerieReserva.SEMANAS_MINIMAS || semanas > SerieReserva.SEMANAS_MAXIMAS) {
            throw new ReservaInvalidaException("Una reserva semanal dura entre " + SerieReserva.SEMANAS_MINIMAS
                    + " y " + SerieReserva.SEMANAS_MAXIMAS + " semanas");
        }
        Recurso recurso = recursoRepository.findById(request.recursoId())
                .filter(Recurso::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException(request.recursoId()));
        Usuario usuario = usuarioRepository.findByEmail(EmailUtils.normalizar(emailUsuario))
                .orElseThrow(() -> new UsuarioNoEncontradoException(emailUsuario));

        // Basta validar la primera semana: las demás tienen la misma hora y el horario no cambia.
        ReservaService.validarHorario(recurso, request.fechaInicio(), request.fechaFin());
        List<Ocurrencia> ocurrencias = generarOcurrencias(request.fechaInicio(), request.fechaFin(), semanas);
        if (!ocurrencias.getFirst().inicio().isAfter(Instant.now(clock))) {
            throw new ReservaInvalidaException("No se puede reservar en el pasado");
        }

        List<LocalDate> enConflicto = fechasEnConflicto(recurso, ocurrencias);
        if (!enConflicto.isEmpty()) {
            throw new ConflictoSerieException(recurso.getNombre(), enConflicto);
        }

        SerieReserva serie = serieRepository.save(new SerieReserva(usuario, recurso, semanas));
        List<Reserva> reservas = ocurrencias.stream()
                .map(o -> new Reserva(usuario, recurso, o.inicio(), o.fin(), serie))
                .toList();
        try {
            reservaRepository.saveAllAndFlush(reservas);
        } catch (DataIntegrityViolationException e) {
            // Carrera: otra petición ocupó una de las fechas entre la comprobación y el INSERT.
            // La excepción hace rollback de TODA la transacción: no queda ninguna reserva ni la serie.
            if (ReservaService.esViolacionDeSolapamiento(e)) {
                throw new ConflictoReservaException(recurso.getNombre());
            }
            throw e;
        }
        eventos.publishEvent(new SerieCreadaEvent(serie.getId()));
        return aResponse(serie, reservas);
    }

    @Transactional(readOnly = true)
    public SerieResponse obtener(Long serieId, String emailUsuario, boolean esAdmin) {
        SerieReserva serie = buscarVisible(serieId, emailUsuario, esAdmin);
        return aResponse(serie, reservaRepository.findBySerieIdOrderByFechaInicio(serieId));
    }

    /**
     * Cancela las fechas de la serie que aún se pueden cancelar, con la misma regla que una
     * reserva suelta (cliente: hasta 2 h antes). Las pasadas y las ya canceladas no se tocan.
     */
    @Transactional
    public SerieResponse cancelarRestantes(Long serieId, String emailUsuario, boolean esAdmin) {
        SerieReserva serie = buscarVisible(serieId, emailUsuario, esAdmin);
        List<Reserva> reservas = reservaRepository.findBySerieIdOrderByFechaInicio(serieId);

        Instant ahora = Instant.now(clock);
        List<Reserva> cancelables = reservas.stream()
                .filter(r -> ReservaService.motivoNoCancelable(r.getEstado(), r.getFechaInicio(), esAdmin, ahora,
                        reservasProperties.anticipacionMinimaCancelacion()) == null)
                .toList();
        if (cancelables.isEmpty()) {
            throw new ReservaNoCancelableException("No quedan fechas de esta serie que se puedan cancelar");
        }

        cancelables.forEach(Reserva::cancelar);
        boolean porAdministrador = esAdmin && !serie.perteneceA(EmailUtils.normalizar(emailUsuario));
        eventos.publishEvent(new SerieCanceladaEvent(serieId,
                cancelables.stream().map(Reserva::getId).toList(), porAdministrador));
        return aResponse(serie, reservas);
    }

    /** La misma franja horaria, semana tras semana (plusWeeks respeta cambios de mes y de año). */
    List<Ocurrencia> generarOcurrencias(LocalDateTime inicio, LocalDateTime fin, int semanas) {
        List<Ocurrencia> ocurrencias = new ArrayList<>(semanas);
        for (int semana = 0; semana < semanas; semana++) {
            LocalDateTime inicioSemana = inicio.plusWeeks(semana);
            ocurrencias.add(new Ocurrencia(inicioSemana.toLocalDate(),
                    aInstante(inicioSemana), aInstante(fin.plusWeeks(semana))));
        }
        return ocurrencias;
    }

    /**
     * Una sola consulta para toda la serie (en vez de una por semana): se traen las reservas
     * no canceladas del recurso entre la primera y la última fecha y se cruzan en memoria.
     */
    private List<LocalDate> fechasEnConflicto(Recurso recurso, List<Ocurrencia> ocurrencias) {
        List<Reserva> existentes = reservaRepository.findNoCanceladasEnRango(recurso.getId(),
                ocurrencias.getFirst().inicio(), ocurrencias.getLast().fin());
        return ocurrencias.stream()
                .filter(o -> existentes.stream().anyMatch(o::seSolapaCon))
                .map(Ocurrencia::dia)
                .toList();
    }

    private SerieReserva buscarVisible(Long serieId, String emailUsuario, boolean esAdmin) {
        SerieReserva serie = serieRepository.findConDetalleById(serieId)
                .orElseThrow(() -> new SerieNoEncontradaException(serieId));
        if (!esAdmin && !serie.perteneceA(EmailUtils.normalizar(emailUsuario))) {
            throw new SerieNoEncontradaException(serieId);
        }
        return serie;
    }

    private SerieResponse aResponse(SerieReserva serie, List<Reserva> reservas) {
        return new SerieResponse(serie.getId(),
                new RecursoResumen(serie.getRecurso().getId(), serie.getRecurso().getNombre()),
                serie.getSemanas(),
                reservas.stream().map(reservaMapper::toResponse).toList());
    }

    private Instant aInstante(LocalDateTime fechaLocal) {
        return fechaLocal.atZone(reservasProperties.zonaHoraria()).toInstant();
    }
}
