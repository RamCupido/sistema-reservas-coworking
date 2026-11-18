-- Momento en que se envió (o se está enviando) el recordatorio de 24 h. NULL = pendiente.
-- La tarea programada lo "reclama" con un UPDATE condicional, así nunca se envía dos veces.
ALTER TABLE reservas ADD COLUMN recordatorio_enviado_en TIMESTAMPTZ;

-- Índice parcial: solo contiene las reservas que aún pueden necesitar recordatorio,
-- así la consulta de la tarea (cada 15 min) no recorre todo el historial.
CREATE INDEX ix_reservas_recordatorio_pendiente ON reservas (fecha_inicio)
    WHERE estado = 'CONFIRMADA' AND recordatorio_enviado_en IS NULL;
