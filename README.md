# Sistema de Reservas — Coworking y Salas de Reuniones

Plataforma web para reservar espacios de coworking y salas de reuniones empresariales:
disponibilidad en tiempo real, prevención de reservas solapadas, cancelaciones y
recordatorios por correo.

> 🚧 Proyecto en construcción, avanzando por fases (ver [Roadmap](#roadmap)).

## Stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, Bean Validation, Actuator
- **Base de datos:** PostgreSQL 17 con migraciones Flyway
- **Seguridad:** Spring Security + JWT (OAuth2 Resource Server), BCrypt
- **Documentación:** springdoc-openapi (Swagger UI)
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

## Documentación de la API (Swagger)

Con la app en marcha: **http://localhost:8080/swagger-ui.html**

Para probar endpoints protegidos: ejecuta `POST /api/v1/auth/login`, copia el `accessToken`,
pulsa **Authorize** y pégalo. La especificación OpenAPI en JSON está en `/v3/api-docs`.

## Autenticación

La API usa **JWT** (HS256) mediante Spring Security OAuth2 Resource Server.

| Método | Ruta | Acceso | Descripción |
|--------|------|--------|-------------|
| `POST` | `/api/v1/auth/registro` | Público | Crea una cuenta con rol `CLIENTE` |
| `POST` | `/api/v1/auth/login` | Público | Devuelve un `accessToken` |
| `GET`  | `/api/v1/usuarios/me` | Autenticado | Datos del usuario del token |

```bash
curl -X POST localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@reservas.local","password":"..."}'

curl localhost:8080/api/v1/usuarios/me -H "Authorization: Bearer <token>"
```

**Administrador inicial:** si `ADMIN_EMAIL` y `ADMIN_PASSWORD` están definidas, la app crea ese
usuario con rol `ADMIN` al arrancar (solo si aún no existe). El registro público nunca crea administradores.

### Variables de entorno

| Variable | Obligatoria | Descripción |
|----------|-------------|-------------|
| `JWT_SECRET` | Sí | Clave HS256, mínimo 32 bytes (`openssl rand -base64 48`) |
| `JWT_EXPIRACION` | No | Duración del token (por defecto `1h`) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | No | Administrador inicial |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Solo en `prod` | Conexión a PostgreSQL |

## Recursos

Espacios reservables: `SALA_REUNIONES`, `ESCRITORIO`, `OFICINA_PRIVADA` y `AUDITORIO`.

| Método | Ruta | Acceso | Descripción |
|--------|------|--------|-------------|
| `GET`    | `/api/v1/recursos?tipo=&capacidadMinima=&page=&size=&sort=` | Público | Catálogo paginado (solo activos) |
| `GET`    | `/api/v1/recursos/{id}` | Público | Detalle de un recurso |
| `POST`   | `/api/v1/recursos` | ADMIN | Crear (201 + `Location`) |
| `PUT`    | `/api/v1/recursos/{id}` | ADMIN | Reemplazar |
| `DELETE` | `/api/v1/recursos/{id}` | ADMIN | Desactivar (borrado lógico) |

Reglas: el horario debe tener la apertura antes del cierre y las horas deben ser en punto o y media
(los bloques de reserva son de 30 minutos). El nombre es único sin distinguir mayúsculas.
Un ADMIN puede listar también los desactivados con `incluirInactivos=true`.

## Errores

Todas las respuestas de error siguen el formato [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807)
(`application/problem+json`):

```json
{
  "type": "about:blank",
  "title": "Datos inválidos",
  "status": 400,
  "detail": "La petición contiene campos inválidos",
  "instance": "/api/v1/recursos",
  "errores": { "capacidad": "La capacidad debe ser mayor que 0" }
}
```

| Código | Cuándo |
|--------|--------|
| 400 | Validación, regla de negocio incumplida, JSON o parámetro inválido |
| 401 | Sin token, token inválido/expirado o credenciales incorrectas |
| 403 | Autenticado pero sin el rol necesario |
| 404 | Recurso o usuario inexistente |
| 409 | Duplicado o modificación concurrente |
| 500 | Error inesperado (sin detalles internos; queda en el log) |

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
- [x] **Fase 1** — Usuarios y autenticación JWT con roles
- [x] **Fase 2** — CRUD de recursos, manejo global de errores, Swagger
- [ ] **Fase 3** — Reservas: solapamiento, concurrencia, cancelación y disponibilidad
- [ ] **Fase 4** — Frontend Thymeleaf
- [ ] **Fase 5** — Notificaciones por correo y recordatorios programados
- [ ] **Fase 6** — Dockerfile, CI con GitHub Actions y despliegue
