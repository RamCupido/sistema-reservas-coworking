-- btree_gist permite combinar igualdad (recurso_id) y rangos (tstzrange) en un mismo
-- índice GiST. Lo necesitaremos si en la Fase 3 usamos un constraint EXCLUDE USING gist
-- para impedir reservas solapadas.
CREATE EXTENSION IF NOT EXISTS btree_gist;
