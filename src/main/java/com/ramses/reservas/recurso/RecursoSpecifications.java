package com.ramses.reservas.recurso;

import org.springframework.data.jpa.domain.Specification;

/** Cada método es una condición WHERE reutilizable; el service combina solo las que apliquen. */
final class RecursoSpecifications {

    private RecursoSpecifications() {
    }

    static Specification<Recurso> activos() {
        return (root, query, cb) -> cb.isTrue(root.get("activo"));
    }

    static Specification<Recurso> deTipo(TipoRecurso tipo) {
        return (root, query, cb) -> cb.equal(root.get("tipo"), tipo);
    }

    static Specification<Recurso> conCapacidadMinima(int capacidad) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("capacidad"), capacidad);
    }
}
