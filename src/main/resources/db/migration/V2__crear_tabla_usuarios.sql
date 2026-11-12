CREATE TABLE usuarios (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password        VARCHAR(100) NOT NULL,
    rol             VARCHAR(20)  NOT NULL,
    fecha_creacion  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_rol CHECK (rol IN ('CLIENTE', 'ADMIN'))
);
