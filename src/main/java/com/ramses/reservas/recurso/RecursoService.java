package com.ramses.reservas.recurso;

import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.common.exception.HorarioInvalidoException;
import com.ramses.reservas.common.exception.NombreRecursoDuplicadoException;
import com.ramses.reservas.common.exception.RecursoNoEncontradoException;
import com.ramses.reservas.recurso.dto.RecursoRequest;
import com.ramses.reservas.recurso.dto.RecursoResponse;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RecursoService {

    private final RecursoRepository recursoRepository;

    /**
     * @param incluirInactivos solo se respeta para administradores (lo decide el controller);
     *                         el público siempre ve únicamente recursos activos.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<RecursoResponse> listar(TipoRecurso tipo, Integer capacidadMinima,
                                                  boolean incluirInactivos, Pageable pageable) {
        List<Specification<Recurso>> filtros = new ArrayList<>();
        if (!incluirInactivos) {
            filtros.add(RecursoSpecifications.activos());
        }
        if (tipo != null) {
            filtros.add(RecursoSpecifications.deTipo(tipo));
        }
        if (capacidadMinima != null) {
            filtros.add(RecursoSpecifications.conCapacidadMinima(capacidadMinima));
        }

        return PaginaResponse.of(
                recursoRepository.findAll(Specification.allOf(filtros), pageable).map(RecursoMapper::toResponse));
    }

    /** Un recurso inactivo es "invisible" para el público: responde 404 igual que si no existiera. */
    @Transactional(readOnly = true)
    public RecursoResponse obtener(Long id, boolean incluirInactivos) {
        Recurso recurso = buscar(id);
        if (!recurso.isActivo() && !incluirInactivos) {
            throw new RecursoNoEncontradoException(id);
        }
        return RecursoMapper.toResponse(recurso);
    }

    public RecursoResponse crear(RecursoRequest request) {
        validarHorario(request.horaApertura(), request.horaCierre());
        if (recursoRepository.existsByNombreIgnoreCase(request.nombre().trim())) {
            throw new NombreRecursoDuplicadoException(request.nombre().trim());
        }
        return RecursoMapper.toResponse(recursoRepository.save(RecursoMapper.toEntity(request)));
    }

    /** No hace falta llamar a save(): la entidad está "managed" y Hibernate detecta los cambios al confirmar. */
    public RecursoResponse actualizar(Long id, RecursoRequest request) {
        Recurso recurso = buscar(id);
        validarHorario(request.horaApertura(), request.horaCierre());
        if (recursoRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre().trim(), id)) {
            throw new NombreRecursoDuplicadoException(request.nombre().trim());
        }
        RecursoMapper.actualizar(recurso, request);
        return RecursoMapper.toResponse(recurso);
    }

    /** Borrado lógico: las reservas históricas seguirán apuntando a este recurso. */
    public void desactivar(Long id) {
        buscar(id).setActivo(false);
    }

    private Recurso buscar(Long id) {
        return recursoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException(id));
    }

    static void validarHorario(LocalTime apertura, LocalTime cierre) {
        if (!esHoraEnPuntoOYMedia(apertura) || !esHoraEnPuntoOYMedia(cierre)) {
            throw new HorarioInvalidoException(
                    "Las horas de apertura y cierre deben ser en punto o y media (por ejemplo 08:00 o 08:30)");
        }
        if (!apertura.isBefore(cierre)) {
            throw new HorarioInvalidoException("La hora de apertura debe ser anterior a la de cierre");
        }
    }

    private static boolean esHoraEnPuntoOYMedia(LocalTime hora) {
        return hora.getMinute() % 30 == 0 && hora.getSecond() == 0 && hora.getNano() == 0;
    }
}
