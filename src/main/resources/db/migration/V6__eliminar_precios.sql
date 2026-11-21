-- El sistema no gestiona pagos: se eliminan los precios de recursos y reservas.
-- Migración nueva (no se editan V3/V4): Flyway valida el checksum de las ya aplicadas,
-- y así cualquier base de datos existente llega al mismo esquema final.
ALTER TABLE reservas
    DROP CONSTRAINT ck_reservas_precio,
    DROP COLUMN precio_total;

ALTER TABLE recursos
    DROP CONSTRAINT ck_recursos_precio,
    DROP COLUMN precio_por_hora;
