package com.ramses.reservas.recurso;

import com.ramses.reservas.recurso.dto.RecursoRequest;
import com.ramses.reservas.recurso.dto.RecursoResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;

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
                recurso.getPrecioPorHora(),
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
                .precioPorHora(normalizarPrecio(request.precioPorHora()))
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
        recurso.setPrecioPorHora(normalizarPrecio(request.precioPorHora()));
        recurso.setHoraApertura(request.horaApertura());
        recurso.setHoraCierre(request.horaCierre());
        if (request.activo() != null) {
            recurso.setActivo(request.activo());
        }
    }

    /** Siempre 2 decimales (30 → 30.00), igual que NUMERIC(10,2) en la BD. */
    private static BigDecimal normalizarPrecio(BigDecimal precio) {
        return precio.setScale(2, RoundingMode.HALF_UP);
    }
}
