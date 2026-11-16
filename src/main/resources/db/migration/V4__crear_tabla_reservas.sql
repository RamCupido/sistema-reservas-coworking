CREATE TABLE reservas (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id      BIGINT        NOT NULL REFERENCES usuarios (id),
    recurso_id      BIGINT        NOT NULL REFERENCES recursos (id),
    fecha_inicio    TIMESTAMPTZ   NOT NULL,
    fecha_fin       TIMESTAMPTZ   NOT NULL,
    estado          VARCHAR(20)   NOT NULL,
    precio_total    NUMERIC(10,2) NOT NULL,
    fecha_creacion  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_reservas_estado CHECK (estado IN ('CONFIRMADA', 'CANCELADA', 'COMPLETADA')),
    CONSTRAINT ck_reservas_rango  CHECK (fecha_inicio < fecha_fin),
    CONSTRAINT ck_reservas_precio CHECK (precio_total >= 0),

    -- Garantía definitiva contra solapamientos, incluso con peticiones simultáneas:
    -- no puede haber dos filas del mismo recurso (=) cuyos rangos se crucen (&&),
    -- ignorando las canceladas. '[)' = incluye el inicio y excluye el fin, así que
    -- 10:00-11:00 y 11:00-12:00 no chocan. Requiere btree_gist (V1) para combinar = con &&.
    CONSTRAINT ex_reservas_solapamiento EXCLUDE USING gist (
        recurso_id WITH =,
        tstzrange(fecha_inicio, fecha_fin, '[)') WITH &&
    ) WHERE (estado <> 'CANCELADA')
);

-- "Mis reservas" ordenadas por fecha
CREATE INDEX ix_reservas_usuario_inicio ON reservas (usuario_id, fecha_inicio DESC);
-- Listado de admin por rango de fechas
CREATE INDEX ix_reservas_inicio ON reservas (fecha_inicio);
