package com.ramses.reservas.reserva;

import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.common.exception.CancelacionFueraDePlazoException;
import com.ramses.reservas.common.exception.ConflictoReservaException;
import com.ramses.reservas.common.exception.FiltroInvalidoException;
import com.ramses.reservas.common.exception.ModificacionFueraDePlazoException;
import com.ramses.reservas.common.exception.RecursoNoEncontradoException;
import com.ramses.reservas.common.exception.ReservaInvalidaException;
import com.ramses.reservas.common.exception.ReservaNoCancelableException;
import com.ramses.reservas.common.exception.ReservaNoEncontradaException;
import com.ramses.reservas.common.exception.ReservaNoModificableException;
import com.ramses.reservas.common.exception.UsuarioNoEncontradoException;
import com.ramses.reservas.common.util.EmailUtils;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.reserva.dto.CrearReservaRequest;
import com.ramses.reservas.reserva.dto.ModificarReservaRequest;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservaService {

    /** SQLState de PostgreSQL para "exclusion_violation" (EXCLUDE USING gist). */
    private static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";
    /** SQLState de PostgreSQL para "deadlock_detected": también ocurre con escrituras simultáneas sobre el EXCLUDE. */
    private static final String SQLSTATE_DEADLOCK = "40P01";
    private static final int MINUTOS_BLOQUE = 30;

    private final ReservaRepository reservaRepository;
    private final RecursoRepository recursoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReservaMapper reservaMapper;
    private final ReservasProperties reservasProperties;
    private final Clock clock;
    private final ApplicationEventPublisher eventos;

    /**
     * Doble protección contra solapamientos:
     * 1) consulta previa → error claro en el caso normal;
     * 2) constraint EXCLUDE en PostgreSQL → atrapa la carrera de dos peticiones simultáneas,
     *    que pasarían ambas la consulta previa antes de que ninguna haya insertado.
     */
    @Transactional
    public ReservaResponse crear(CrearReservaRequest request, String emailUsuario) {
        Recurso recurso = recursoRepository.findById(request.recursoId())
                .filter(Recurso::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException(request.recursoId()));
        Usuario usuario = buscarUsuario(emailUsuario);

        LocalDateTime inicio = request.fechaInicio();
        LocalDateTime fin = request.fechaFin();
        validarHorario(recurso, inicio, fin);

        Instant inicioUtc = aInstante(inicio);
        Instant finUtc = aInstante(fin);
        if (!inicioUtc.isAfter(Instant.now(clock))) {
            throw new ReservaInvalidaException("No se puede reservar en el pasado");
        }
        if (reservaRepository.existeSolapamiento(recurso.getId(), inicioUtc, finUtc)) {
            throw new ConflictoReservaException(recurso.getNombre());
        }

        Reserva guardada = guardarSinSolapamiento(new Reserva(usuario, recurso, inicioUtc, finUtc));
        // Se publica dentro de la transacción, pero el listener de correo solo actúa tras el
        // COMMIT (@TransactionalEventListener): si algo hiciera rollback, no se envía nada.
        eventos.publishEvent(new ReservaCreadaEvent(guardada.getId()));
        return reservaMapper.toResponse(guardada);
    }

    @Transactional(readOnly = true)
    public ReservaResponse obtener(Long id, String emailUsuario, boolean esAdmin) {
        return reservaMapper.toResponse(buscarVisible(id, emailUsuario, esAdmin));
    }

    @Transactional
    public ReservaResponse cancelar(Long id, String emailUsuario, boolean esAdmin) {
        Reserva reserva = buscarVisible(id, emailUsuario, esAdmin);
        RuntimeException motivo = motivoNoCancelable(reserva.getEstado(), reserva.getFechaInicio(), esAdmin);
        if (motivo != null) {
            throw motivo;
        }

        // Sin save(): la entidad está gestionada; al confirmar, Hibernate hace el UPDATE
        // con "WHERE version = ?" (bloqueo optimista).
        reserva.cancelar();
        boolean porAdministrador = esAdmin && !reserva.perteneceA(EmailUtils.normalizar(emailUsuario));
        eventos.publishEvent(new ReservaCanceladaEvent(reserva.getId(), porAdministrador));
        return reservaMapper.toResponse(reserva);
    }

    /**
     * Cambia espacio, día y horario de una reserva individual. Mismas validaciones que {@link #crear},
     * pero el solapamiento no cuenta la propia reserva (moverla media hora sobre sí misma es válido).
     * La fila se actualiza (mismo id): el EXCLUDE de PostgreSQL no compara una fila consigo misma.
     */
    @Transactional
    public ReservaResponse modificar(Long id, ModificarReservaRequest request, String emailUsuario, boolean esAdmin) {
        Reserva reserva = buscarVisible(id, emailUsuario, esAdmin);
        RuntimeException motivo = motivoNoModificable(reserva.getEstado(), reserva.getFechaInicio(),
                reserva.getSerie() != null, esAdmin);
        if (motivo != null) {
            throw motivo;
        }
        Recurso recurso = recursoRepository.findById(request.recursoId())
                .filter(Recurso::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException(request.recursoId()));
        validarHorario(recurso, request.fechaInicio(), request.fechaFin());
        Instant inicioUtc = aInstante(request.fechaInicio());
        Instant finUtc = aInstante(request.fechaFin());
        if (!inicioUtc.isAfter(Instant.now(clock))) {
            throw new ReservaInvalidaException("No se puede reservar en el pasado");
        }

        ReservaResponse antes = reservaMapper.toResponse(reserva);
        boolean sinCambios = antes.recurso().id().equals(recurso.getId())
                && reserva.getFechaInicio().equals(inicioUtc) && reserva.getFechaFin().equals(finUtc);
        if (sinCambios) {
            return antes;
        }
        if (reservaRepository.existeSolapamientoExcluyendo(recurso.getId(), inicioUtc, finUtc, reserva.getId())) {
            throw new ConflictoReservaException(recurso.getNombre());
        }

        reserva.reprogramar(recurso, inicioUtc, finUtc);
        Reserva guardada = guardarSinSolapamiento(reserva);
        // Tras el flush: el recordatorio ya enviado era del horario anterior, hay que volver a enviarlo.
        reservaRepository.liberarRecordatorio(guardada.getId());
        boolean porAdministrador = esAdmin && !reserva.perteneceA(EmailUtils.normalizar(emailUsuario));
        eventos.publishEvent(new ReservaModificadaEvent(guardada.getId(), antes.recurso().nombre(),
                antes.fechaInicio(), antes.fechaFin(), porAdministrador));
        return reservaMapper.toResponse(guardada);
    }

    /** Para abrir la agenda en "modo modificar": la reserva si se puede modificar, o la excepción que lo impide. */
    @Transactional(readOnly = true)
    public ReservaResponse obtenerModificable(Long id, String emailUsuario, boolean esAdmin) {
        Reserva reserva = buscarVisible(id, emailUsuario, esAdmin);
        RuntimeException motivo = motivoNoModificable(reserva.getEstado(), reserva.getFechaInicio(),
                reserva.getSerie() != null, esAdmin);
        if (motivo != null) {
            throw motivo;
        }
        return reservaMapper.toResponse(reserva);
    }

    /** Misma regla que {@link #modificar}, como pregunta: muestra u oculta el botón "Modificar". */
    public boolean esModificable(ReservaResponse reserva, boolean esAdmin) {
        return motivoNoModificable(reserva.estado(), aInstante(reserva.fechaInicio()), reserva.serieId() != null,
                esAdmin) == null;
    }

    /**
     * Misma regla que {@link #cancelar}, pero como pregunta: la usa el frontend para
     * mostrar u ocultar el botón "Cancelar" sin duplicar la lógica.
     */
    public boolean esCancelable(ReservaResponse reserva, boolean esAdmin) {
        return motivoNoCancelable(reserva.estado(), aInstante(reserva.fechaInicio()), esAdmin) == null;
    }

    /** Reservas no canceladas que aún no empiezan (para avisar al admin al desactivar un recurso). */
    @Transactional(readOnly = true)
    public long contarFuturas(Long recursoId) {
        return reservaRepository.contarFuturasNoCanceladas(recursoId, Instant.now(clock));
    }

    /** Tarea programada: las confirmadas que ya terminaron pasan a COMPLETADA. Devuelve cuántas. */
    @Transactional
    public int completarFinalizadas() {
        return reservaRepository.completarFinalizadas(Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ReservaResponse> listarMias(String emailUsuario, EstadoReserva estado, Pageable pageable) {
        String email = EmailUtils.normalizar(emailUsuario);
        var pagina = estado == null
                ? reservaRepository.findByUsuarioEmail(email, pageable)
                : reservaRepository.findByUsuarioEmailAndEstado(email, estado, pageable);
        return PaginaResponse.of(pagina.map(reservaMapper::toResponse));
    }

    /**
     * Reservas del usuario (de cualquier estado) cuyo día de inicio, en la zona del negocio,
     * está entre desde y hasta, ambos inclusivos. Sin paginar: lo usa el calendario mensual,
     * que como mucho muestra 6 semanas.
     */
    @Transactional(readOnly = true)
    public List<ReservaResponse> listarMiasEntre(String emailUsuario, LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new FiltroInvalidoException("'desde' no puede ser posterior a 'hasta'");
        }
        ZoneId zona = reservasProperties.zonaHoraria();
        return reservaRepository.findDeUsuarioEntre(EmailUtils.normalizar(emailUsuario),
                        desde.atStartOfDay(zona).toInstant(), hasta.plusDays(1).atStartOfDay(zona).toInstant())
                .stream().map(reservaMapper::toResponse).toList();
    }

    /** La próxima reserva confirmada que aún no ha empezado (para la página de inicio). */
    @Transactional(readOnly = true)
    public Optional<ReservaResponse> proxima(String emailUsuario) {
        return reservaRepository.findFirstByUsuarioEmailAndEstadoAndFechaInicioAfterOrderByFechaInicioAsc(
                        EmailUtils.normalizar(emailUsuario), EstadoReserva.CONFIRMADA, Instant.now(clock))
                .map(reservaMapper::toResponse);
    }

    /** desde/hasta filtran por el día de inicio (en la zona del negocio), ambos inclusivos. */
    @Transactional(readOnly = true)
    public PaginaResponse<ReservaResponse> listarTodas(EstadoReserva estado, LocalDate desde, LocalDate hasta,
                                                       Pageable pageable) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new FiltroInvalidoException("'desde' no puede ser posterior a 'hasta'");
        }
        ZoneId zona = reservasProperties.zonaHoraria();
        List<Specification<Reserva>> filtros = new ArrayList<>();
        if (estado != null) {
            filtros.add((root, query, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (desde != null) {
            Instant limite = desde.atStartOfDay(zona).toInstant();
            filtros.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaInicio"), limite));
        }
        if (hasta != null) {
            Instant limite = hasta.plusDays(1).atStartOfDay(zona).toInstant();
            filtros.add((root, query, cb) -> cb.lessThan(root.get("fechaInicio"), limite));
        }
        return PaginaResponse.of(
                reservaRepository.findAll(Specification.allOf(filtros), pageable).map(reservaMapper::toResponse));
    }

    // --- Reglas de negocio ---

    /** Regla 3: mismo día, dentro del horario de atención, en bloques de 30 minutos. */
    static void validarHorario(Recurso recurso, LocalDateTime inicio, LocalDateTime fin) {
        if (!fin.isAfter(inicio)) {
            throw new ReservaInvalidaException("La fecha de fin debe ser posterior a la de inicio");
        }
        if (!alineadoABloque(inicio.toLocalTime()) || !alineadoABloque(fin.toLocalTime())) {
            throw new ReservaInvalidaException(
                    "Las reservas se hacen en bloques de 30 minutos: inicio y fin deben ser en punto o y media");
        }
        if (!inicio.toLocalDate().equals(fin.toLocalDate())) {
            throw new ReservaInvalidaException("La reserva debe empezar y terminar el mismo día");
        }
        if (inicio.toLocalTime().isBefore(recurso.getHoraApertura())
                || fin.toLocalTime().isAfter(recurso.getHoraCierre())) {
            throw new ReservaInvalidaException("El horario de atención de '" + recurso.getNombre() + "' es de "
                    + recurso.getHoraApertura() + " a " + recurso.getHoraCierre());
        }
    }

    /**
     * Regla 4. Devuelve la excepción que explica por qué no se puede cancelar, o null si se puede.
     * Devolverla (en vez de lanzarla) permite reutilizar la regla también como booleano.
     */
    private RuntimeException motivoNoCancelable(EstadoReserva estado, Instant inicio, boolean esAdmin) {
        return motivoNoCancelable(estado, inicio, esAdmin, Instant.now(clock),
                reservasProperties.anticipacionMinimaCancelacion());
    }

    /** Estática y sin dependencias: la reutiliza {@link SerieReservaService} para "cancelar las restantes". */
    static RuntimeException motivoNoCancelable(EstadoReserva estado, Instant inicio, boolean esAdmin,
                                               Instant ahora, Duration anticipacion) {
        if (estado == EstadoReserva.CANCELADA) {
            return new ReservaNoCancelableException("La reserva ya está cancelada");
        }
        if (estado == EstadoReserva.COMPLETADA || !inicio.isAfter(ahora)) {
            return new ReservaNoCancelableException("La reserva ya comenzó o finalizó");
        }
        if (!esAdmin && Duration.between(ahora, inicio).compareTo(anticipacion) < 0) {
            return new CancelacionFueraDePlazoException(anticipacion);
        }
        return null;
    }

    /**
     * Regla para modificar: solo reservas individuales, con el mismo estado y plazo que para cancelar,
     * pero con mensajes propios ("modificar", no "cancelar"). Devuelve la excepción o null.
     */
    private RuntimeException motivoNoModificable(EstadoReserva estado, Instant inicio, boolean enSerie,
                                                 boolean esAdmin) {
        if (enSerie) {
            return new ReservaNoModificableException("Las reservas semanales no se pueden modificar");
        }
        Duration anticipacion = reservasProperties.anticipacionMinimaCancelacion();
        RuntimeException motivo = motivoNoCancelable(estado, inicio, esAdmin, Instant.now(clock), anticipacion);
        if (motivo instanceof CancelacionFueraDePlazoException) {
            return new ModificacionFueraDePlazoException(anticipacion);
        }
        return motivo == null ? null : new ReservaNoModificableException(motivo.getMessage());
    }

    /**
     * saveAndFlush fuerza el INSERT/UPDATE ahora (no al hacer commit) para poder traducir aquí
     * la violación del EXCLUDE (dos peticiones simultáneas por el mismo hueco) a un 409.
     * Con escrituras simultáneas, PostgreSQL puede abortar con un deadlock en vez de 23P01:
     * para el usuario es lo mismo (el hueco ya no está libre), así que también es un 409.
     */
    private Reserva guardarSinSolapamiento(Reserva reserva) {
        try {
            return reservaRepository.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            if (esViolacionDeSolapamiento(e)) {
                throw new ConflictoReservaException(reserva.getRecurso().getNombre());
            }
            throw e;
        } catch (PessimisticLockingFailureException e) {
            if (tieneSqlState(e, SQLSTATE_DEADLOCK)) {
                throw new ConflictoReservaException(reserva.getRecurso().getNombre());
            }
            throw e;
        }
    }

    private static boolean alineadoABloque(LocalTime hora) {
        return hora.getMinute() % MINUTOS_BLOQUE == 0 && hora.getSecond() == 0 && hora.getNano() == 0;
    }

    /** Recorre la cadena de causas buscando el SQLException original de PostgreSQL. */
    static boolean esViolacionDeSolapamiento(Throwable error) {
        return tieneSqlState(error, SQLSTATE_EXCLUSION_VIOLATION);
    }

    private static boolean tieneSqlState(Throwable error, String sqlState) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    // --- Utilidades ---

    /** Una reserva ajena responde 404 (no 403): así no se confirma que ese id existe. */
    private Reserva buscarVisible(Long id, String emailUsuario, boolean esAdmin) {
        Reserva reserva = reservaRepository.findConDetalleById(id)
                .orElseThrow(() -> new ReservaNoEncontradaException(id));
        if (!esAdmin && !reserva.perteneceA(EmailUtils.normalizar(emailUsuario))) {
            throw new ReservaNoEncontradaException(id);
        }
        return reserva;
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(EmailUtils.normalizar(email))
                .orElseThrow(() -> new UsuarioNoEncontradoException(email));
    }

    private Instant aInstante(LocalDateTime fechaLocal) {
        return fechaLocal.atZone(reservasProperties.zonaHoraria()).toInstant();
    }
}
