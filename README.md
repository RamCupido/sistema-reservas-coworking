# Sistema de Reservas — Coworking y Salas de Reuniones

Plataforma web para reservar espacios de coworking y salas de reuniones empresariales:
disponibilidad en tiempo real, prevención de reservas solapadas, cancelaciones y
recordatorios por correo.

> 🚧 Proyecto en construcción, avanzando por fases (ver [Roadmap](#roadmap)).

## Stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, Bean Validation, Actuator
- **Base de datos:** PostgreSQL 17 con migraciones Flyway
- **Seguridad:** Spring Security + JWT *(Fase 1)*
- **Frontend:** Thymeleaf + Bootstrap *(Fase 4)*
- **Tests:** JUnit 5, Mockito, MockMvc, Testcontainers
- **Infra:** Docker, docker-compose, GitHub Actions *(Fase 6)*

## Requisitos

- JDK 21
- Docker Desktop (para la base de datos y para los tests con Testcontainers)

No hace falta instalar Maven: el proyecto incluye el Maven Wrapper (`./mvnw`).

## Puesta en marcha

```bash
# 1. Variables de entorno (una sola vez)
cp .env.example .env

# 2. Levantar PostgreSQL
docker compose up -d db

# 3. Ejecutar la aplicación con el perfil dev
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

En Windows (PowerShell) usa `.\mvnw.cmd` en lugar de `./mvnw`.

El archivo `.env` lo leen tanto `docker-compose` como el perfil `dev` de Spring
(`spring.config.import`). Si ya tienes otro PostgreSQL en el puerto 5432, cambia
`POSTGRES_PORT` en `.env` y listo.

Comprueba que la app está viva en: http://localhost:8080/actuator/health

## Tests

```bash
./mvnw test
```

Los tests de integración levantan un PostgreSQL real en un contenedor con Testcontainers,
así que Docker tiene que estar en marcha.

## Perfiles

| Perfil | Uso | Base de datos |
|--------|-----|---------------|
| `dev`  | Desarrollo local | PostgreSQL de `docker-compose` |
| `test` | Tests automáticos | Contenedor efímero de Testcontainers |
| `prod` | Despliegue | Variables `DB_URL`, `DB_USER` y `DB_PASSWORD` (obligatorias) |

Los secretos nunca se guardan en el repositorio: se leen desde variables de entorno.

## Roadmap

- [x] **Fase 0** — Esqueleto: Maven, docker-compose, perfiles, Flyway, healthcheck
- [ ] **Fase 1** — Usuarios y autenticación JWT con roles
- [ ] **Fase 2** — CRUD de recursos, manejo global de errores, Swagger
- [ ] **Fase 3** — Reservas: solapamiento, concurrencia, cancelación y disponibilidad
- [ ] **Fase 4** — Frontend Thymeleaf
- [ ] **Fase 5** — Notificaciones por correo y recordatorios programados
- [ ] **Fase 6** — Dockerfile, CI con GitHub Actions y despliegue
