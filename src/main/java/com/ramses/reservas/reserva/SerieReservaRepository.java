package com.ramses.reservas.reserva;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SerieReservaRepository extends JpaRepository<SerieReserva, Long> {

    @EntityGraph(attributePaths = {"recurso", "usuario"})
    Optional<SerieReserva> findConDetalleById(Long id);
}
