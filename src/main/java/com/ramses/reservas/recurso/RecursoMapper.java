package com.ramses.reservas.recurso;

import com.ramses.reservas.recurso.dto.RecursoRequest;
import com.ramses.reservas.recurso.dto.RecursoResponse;

public final class RecursoMapper {

    private RecursoMapper() {
    }

    public static RecursoResponse toResponse(Recurso recurso) {
        return new RecursoResponse(
                recurso.getId(),
                recurso.getNombre(),
                recurso.getDescripcion(),
                recurso.getTipo(),
                recurso.getCapacidad(),
                recurso.isActivo(),
                recurso.getHoraApertura(),
                recurso.getHoraCierre());
    }

    public static Recurso toEntity(RecursoRequest request) {
        return Recurso.builder()
                .nombre(request.nombre().trim())
                .descripcion(request.descripcion())
                .tipo(request.tipo())
                .capacidad(request.capacidad())
                .activo(request.activo() == null || request.activo())
                .horaApertura(request.horaApertura())
                .horaCierre(request.horaCierre())
                .build();
    }

    /** PUT: reemplaza todos los campos; activo solo cambia si viene informado. */
    public static void actualizar(Recurso recurso, RecursoRequest request) {
        recurso.setNombre(request.nombre().trim());
        recurso.setDescripcion(request.descripcion());
        recurso.setTipo(request.tipo());
        recurso.setCapacidad(request.capacidad());
        recurso.setHoraApertura(request.horaApertura());
        recurso.setHoraCierre(request.horaCierre());
        if (request.activo() != null) {
            recurso.setActivo(request.activo());
        }
    }
}
