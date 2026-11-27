-- Reservas recurrentes: una serie agrupa varias reservas normales (una por semana).
-- Cada reserva de la serie sigue siendo una fila de "reservas", así que el constraint
-- anti-solapamiento, los recordatorios y la cancelación individual funcionan sin cambios.
CREATE TABLE series_reserva (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id      BIGINT      NOT NULL REFERENCES usuarios (id),
    recurso_id      BIGINT      NOT NULL REFERENCES recursos (id),
    semanas         INTEGER     NOT NULL,
    fecha_creacion  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_series_reserva_semanas CHECK (semanas BETWEEN 2 AND 12)
);

-- NULL = reserva suelta (todas las anteriores a esta migración).
ALTER TABLE reservas ADD COLUMN serie_id BIGINT REFERENCES series_reserva (id);

-- Índice parcial: solo las reservas que pertenecen a una serie (para "cancelar las restantes").
CREATE INDEX ix_reservas_serie ON reservas (serie_id) WHERE serie_id IS NOT NULL;
