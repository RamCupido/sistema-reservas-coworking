package com.ramses.reservas.common.exception;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

/** Alguna fecha de una reserva semanal choca con otra reserva: no se crea ninguna (todo o nada). */
@Getter
public class ConflictoSerieException extends ConflictoException {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final List<LocalDate> fechas;

    public ConflictoSerieException(String nombreRecurso, List<LocalDate> fechas) {
        super("Horario no disponible", "'" + nombreRecurso + "' ya está reservado "
                + (fechas.size() == 1 ? "el " : "en estas fechas: ")
                + fechas.stream().map(FORMATO::format).collect(Collectors.joining(", "))
                + ". No se ha creado ninguna reserva de la serie");
        this.fechas = List.copyOf(fechas);
    }
}
