CREATE TABLE recursos (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre           VARCHAR(100)  NOT NULL,
    descripcion      VARCHAR(1000),
    tipo             VARCHAR(30)   NOT NULL,
    capacidad        INTEGER       NOT NULL,
    precio_por_hora  NUMERIC(10,2) NOT NULL,
    activo           BOOLEAN       NOT NULL DEFAULT TRUE,
    hora_apertura    TIME          NOT NULL,
    hora_cierre      TIME          NOT NULL,

    CONSTRAINT ck_recursos_tipo      CHECK (tipo IN ('SALA_REUNIONES', 'ESCRITORIO', 'OFICINA_PRIVADA', 'AUDITORIO')),
    CONSTRAINT ck_recursos_capacidad CHECK (capacidad > 0),
    CONSTRAINT ck_recursos_precio    CHECK (precio_por_hora >= 0),
    CONSTRAINT ck_recursos_horario   CHECK (hora_apertura < hora_cierre),
    -- Horas en punto o y media, para que los bloques de 30 min queden alineados.
    CONSTRAINT ck_recursos_media_hora CHECK (
        EXTRACT(MINUTE FROM hora_apertura) IN (0, 30) AND EXTRACT(SECOND FROM hora_apertura) = 0 AND
        EXTRACT(MINUTE FROM hora_cierre)   IN (0, 30) AND EXTRACT(SECOND FROM hora_cierre)   = 0)
);

-- Único sin distinguir mayúsculas: "Sala Andes" y "sala andes" son el mismo nombre.
CREATE UNIQUE INDEX uk_recursos_nombre ON recursos (lower(nombre));
