package com.ramses.reservas.common.exception;

/** Igual que con las reservas: una serie ajena responde 404, sin revelar que existe. */
public class SerieNoEncontradaException extends EntidadNoEncontradaException {

    public SerieNoEncontradaException(Long id) {
        super("Serie no encontrada", "No existe una serie de reservas con id " + id);
    }
}
