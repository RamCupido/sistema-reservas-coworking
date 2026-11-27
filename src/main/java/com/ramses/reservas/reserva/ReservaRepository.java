package com.ramses.reservas.reserva;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las consultas de listado usan @EntityGraph para traer recurso/usuario en el mismo SELECT
 * (JOIN). Sin eso, mapear N reservas dispararía N consultas extra (problema N+1).
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {

    /** Regla de solapamiento: inicioA < finB AND inicioB < finA, ignorando canceladas. */
    @Query("""
            SELECT COUNT(r) > 0 FROM Reserva r
            WHERE r.recurso.id = :recursoId
              AND r.estado <> com.ramses.reservas.reserva.EstadoReserva.CANCELADA
              AND r.fechaInicio < :fin
              AND :inicio < r.fechaFin
            """)
    boolean existeSolapamiento(@Param("recursoId") Long recursoId,
                               @Param("inicio") Instant inicio,
                               @Param("fin") Instant fin);

    /** Reservas no canceladas que tocan el intervalo (para calcular la disponibilidad de un día). */
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.recurso.id = :recursoId
              AND r.estado <> com.ramses.reservas.reserva.EstadoReserva.CANCELADA
              AND r.fechaInicio < :fin
              AND :inicio < r.fechaFin
            ORDER BY r.fechaInicio
            """)
    List<Reserva> findNoCanceladasEnRango(@Param("recursoId") Long recursoId,
                                         @Param("inicio") Instant inicio,
                                         @Param("fin") Instant fin);

    @Query("""
            SELECT COUNT(r) FROM Reserva r
            WHERE r.recurso.id = :recursoId
              AND r.estado <> com.ramses.reservas.reserva.EstadoReserva.CANCELADA
              AND r.fechaInicio > :ahora
            """)
    long contarFuturasNoCanceladas(@Param("recursoId") Long recursoId, @Param("ahora") Instant ahora);

    @EntityGraph(attributePaths = {"recurso", "usuario"})
    Optional<Reserva> findConDetalleById(Long id);

    /** Todas las reservas de una serie, en orden cronológico. */
    @EntityGraph(attributePaths = {"recurso", "usuario"})
    List<Reserva> findBySerieIdOrderByFechaInicio(Long serieId);

    @EntityGraph(attributePaths = {"recurso", "usuario"})
    Page<Reserva> findByUsuarioEmail(String email, Pageable pageable);

    /** Reservas del usuario que empiezan en [desde, hasta), para el calendario. */
    @EntityGraph(attributePaths = {"recurso", "usuario"})
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.usuario.email = :email
              AND r.fechaInicio >= :desde
              AND r.fechaInicio < :hasta
            ORDER BY r.fechaInicio
            """)
    List<Reserva> findDeUsuarioEntre(@Param("email") String email,
                                     @Param("desde") Instant desde,
                                     @Param("hasta") Instant hasta);

    @EntityGraph(attributePaths = {"recurso", "usuario"})
    Optional<Reserva> findFirstByUsuarioEmailAndEstadoAndFechaInicioAfterOrderByFechaInicioAsc(
            String email, EstadoReserva estado, Instant despuesDe);

    @EntityGraph(attributePaths = {"recurso", "usuario"})
    Page<Reserva> findByUsuarioEmailAndEstado(String email, EstadoReserva estado, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"recurso", "usuario"})
    Page<Reserva> findAll(Specification<Reserva> spec, Pageable pageable);

    // --- Tareas programadas ---

    /** Confirmadas sin recordatorio que empiezan en (ahora, limite]. */
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.estado = com.ramses.reservas.reserva.EstadoReserva.CONFIRMADA
              AND r.recordatorioEnviadoEn IS NULL
              AND r.fechaInicio > :ahora
              AND r.fechaInicio <= :limite
            ORDER BY r.fechaInicio
            """)
    List<Reserva> findCandidatasARecordatorio(@Param("ahora") Instant ahora, @Param("limite") Instant limite);

    /**
     * "Reclama" el recordatorio de forma atómica: solo una ejecución (o una instancia de la app,
     * si hubiera varias) consigue pasar de NULL a un valor. Devuelve 1 si lo consiguió, 0 si no.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE Reserva r SET r.recordatorioEnviadoEn = :ahora
            WHERE r.id = :id
              AND r.recordatorioEnviadoEn IS NULL
              AND r.estado = com.ramses.reservas.reserva.EstadoReserva.CONFIRMADA
            """)
    int reclamarRecordatorio(@Param("id") Long id, @Param("ahora") Instant ahora);

    /** Si el envío falla, se libera para reintentarlo en la siguiente ejecución. */
    @Modifying
    @Transactional
    @Query("UPDATE Reserva r SET r.recordatorioEnviadoEn = NULL WHERE r.id = :id")
    int liberarRecordatorio(@Param("id") Long id);

    /**
     * Un UPDATE masivo no pasa por la entidad, así que @Version no se incrementa solo:
     * se suma a mano para que una transacción que tenga la reserva cargada detecte el cambio.
     */
    @Modifying
    @Query("""
            UPDATE Reserva r
            SET r.estado = com.ramses.reservas.reserva.EstadoReserva.COMPLETADA, r.version = r.version + 1
            WHERE r.estado = com.ramses.reservas.reserva.EstadoReserva.CONFIRMADA
              AND r.fechaFin <= :ahora
            """)
    int completarFinalizadas(@Param("ahora") Instant ahora);
}
