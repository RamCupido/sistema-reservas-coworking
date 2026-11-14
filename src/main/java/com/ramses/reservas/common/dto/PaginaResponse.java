package com.ramses.reservas.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Contrato de paginación propio en lugar de serializar el Page de Spring, cuyo JSON
 * no es estable entre versiones. Así el frontend depende de algo que controlamos.
 */
public record PaginaResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PaginaResponse<T> of(Page<T> page) {
        return new PaginaResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
