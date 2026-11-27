# Sistema de Reservas — Coworking y Salas de Reuniones

[![CI](https://github.com/RamCupido/sistema-reservas-coworking/actions/workflows/ci.yml/badge.svg)](https://github.com/RamCupido/sistema-reservas-coworking/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL-17-blue)

### 🌐 [Ver la demo en vivo → reservas-coworking.onrender.com](https://reservas-coworking.onrender.com)

> La demo está en un servidor gratuito que se duerme cuando nadie la usa: la primera visita puede tardar
> 1–2 minutos en cargar. Después va con normalidad.

## En pocas palabras

Imagina un edificio de coworking con salas de reuniones, escritorios, oficinas privadas y un auditorio.
Este proyecto es la **aplicación web para reservarlos**:

- Cualquiera puede ver los espacios y **qué horarios están libres** cada día.
- Quien tiene cuenta **reserva** un horario, recibe un **correo de confirmación** y, 24 horas antes, un
  **recordatorio**. Si no puede ir, **cancela** desde la web.
- La administración **gestiona los espacios** (crear, editar, desactivar) y **ve todas las reservas**.

Lo más importante que resuelve: **nunca dos personas pueden reservar el mismo espacio a la misma hora**,
ni siquiera si pulsan "Reservar" exactamente en el mismo instante.

Además de la web, ofrece una **API REST** documentada, lista para conectar una app móvil o un frontend en React.

> Proyecto de portafolio construido por fases, con tests automáticos y verificación continua en GitHub Actions.

---

## ¿Qué se puede hacer?

| Quién | Qué puede hacer |
|-------|-----------------|
| **Visitante** (sin cuenta) | Ver la presentación del servicio, el catálogo de espacios (filtrable por tipo) y la agenda de cualquier día |
| **Cliente** (con cuenta) | Todo lo anterior + reservar (una vez o **cada semana**, de 2 a 12 semanas), ver su **calendario mensual** de reservas y cancelar las suyas hasta 2 horas antes |
| **Administrador** | Todo lo anterior + crear, editar, desactivar y reactivar espacios; ver y cancelar cualquier reserva |

Tipos de espacio: **sala de reuniones**, **escritorio**, **oficina privada** y **auditorio**.
Cada uno tiene capacidad y horario de atención (por ejemplo, de 08:00 a 20:00).

## Cómo se vive una reserva

1. **Elegir un espacio** en el catálogo y abrir su **agenda**: el día aparece dividido en bloques de 30 minutos,
   en verde los libres y tachados los ocupados o ya pasados.
2. **Seleccionar el horario** pulsando el bloque de inicio y el de fin. Si se necesita todas las semanas, se
   marca **"Repetir cada semana"** y se elige durante cuántas (de 2 a 12). Si alguna de esas fechas ya está
   ocupada, no se reserva ninguna y se avisa de cuáles chocan.
3. **Confirmar.** El servidor vuelve a comprobarlo todo: horario de atención, que siga libre y que no sea en el
   pasado.
4. **Llega un correo** de confirmación con los detalles.
5. **24 horas antes** llega un recordatorio.
6. En **"Mis reservas"** aparece en un **calendario mensual** (o, si se prefiere, en una lista con filtros).
   Desde ahí se **cancela** (hasta 2 horas antes) y llega un correo de cancelación.
7. Cuando el horario termina, la reserva pasa sola al estado **completada**.

> No hay precios ni pagos: reservar es gratuito y las reservas nacen confirmadas.

## Cómo está construido

La aplicación sigue una **arquitectura por capas**. Una analogía útil es un restaurante:

| Capa | En el restaurante | En el proyecto |
|------|-------------------|----------------|
| **Controllers** | El camarero: toma el pedido y entrega el plato, pero no cocina | Reciben la petición (página web o API), validan el formato y devuelven la respuesta |
| **Services** | La cocina: donde se aplican las recetas | Toda la lógica de negocio: solapamientos, horarios, plazos |
| **Repositories** | La despensa | Leen y guardan datos en PostgreSQL |

La web y la API son **dos puertas de entrada a la misma cocina**: ambas usan los mismos services, así que
las reglas de negocio están escritas una sola vez.

## Tecnologías

| Tecnología | Para qué se usa aquí |
|------------|----------------------|
| **Java 21** + **Spring Boot 3.5** | Lenguaje y framework base de toda la aplicación |
| **Spring Web** | Atender las peticiones HTTP (páginas y API) |
| **Spring Data JPA** + Hibernate | Convertir objetos Java en filas de la base de datos y al revés |
| **PostgreSQL 17** | Base de datos; además garantiza que no haya reservas solapadas |
| **Flyway** | Versionar los cambios de la base de datos (`V1__...sql`, `V2__...sql`...) |
| **Spring Security** + **JWT** | Login, roles (cliente/admin) y protección de cada ruta |
| **Bean Validation** | Validar datos de entrada ("el email es obligatorio", "capacidad > 0"...) |
| **Thymeleaf** + **Bootstrap 5** | Generar las páginas HTML en el servidor con un diseño responsive |
| **Spring Mail** + `@Async` + `@Scheduled` | Enviar correos en segundo plano y ejecutar tareas periódicas |
| **springdoc-openapi** | Documentación interactiva de la API (Swagger UI) |
| **JUnit 5**, **Mockito**, **MockMvc**, **Testcontainers** | Tests automáticos, incluidos tests contra un PostgreSQL real |
| **Docker** + **Docker Compose** | Empaquetar la aplicación en una imagen y levantarla en local con PostgreSQL y Mailpit |
| **Render** + **Neon** | Publicar la demo gratis: servidor y base de datos PostgreSQL |
| **GitHub Actions** | Ejecutar todos los tests en cada push y pull request |

## Ejecutarlo en tu ordenador

### Requisitos

- **JDK 21**
- **Docker Desktop** (para PostgreSQL, Mailpit y los tests)
- No hace falta instalar Maven: el proyecto incluye el Maven Wrapper (`./mvnw`).

### Pasos

```bash
# 1. Clonar el repositorio
git clone https://github.com/RamCupido/sistema-reservas-coworking.git
cd sistema-reservas-coworking

# 2. Crear tu archivo de configuración local (no se sube a git)
cp .env.example .env
#    Edita .env: pon un JWT_SECRET aleatorio y la contraseña del administrador

# 3. Levantar PostgreSQL y Mailpit
docker compose up -d db mailpit

# 4. Arrancar la aplicación
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

En Windows (PowerShell) usa `.\mvnw.cmd` en lugar de `./mvnw`. Si ya tienes otro PostgreSQL en el puerto 5432,
cambia `POSTGRES_PORT` en `.env`.

### Qué abrir

| Dirección | Qué es |
|-----------|--------|
| http://localhost:8080 | **La aplicación web** |
| http://localhost:8025 | Bandeja de Mailpit: los correos que envía la app |
| http://localhost:8080/swagger-ui.html | Documentación interactiva de la API |


### Primer uso

1. Entra con el administrador definido en `.env` (`ADMIN_EMAIL` / `ADMIN_PASSWORD`); se crea solo al arrancar.
2. En **Administración → Espacios**, crea uno o varios espacios.
3. Cierra sesión, **crea una cuenta de cliente** y haz tu primera reserva.
4. Mira el correo de confirmación en http://localhost:8025.

### Todo con Docker (sin instalar Java)

```bash
cp .env.example .env    # y edítalo como en el paso 2
docker compose up --build
```

Construye la aplicación con el `Dockerfile` y la arranca junto a PostgreSQL y Mailpit, con el mismo límite de
memoria que en internet (512 MB).

## Publicarla en internet

La aplicación está preparada para publicarse **gratis**: el servidor en [Render](https://render.com) y la base
de datos en [Neon](https://neon.com). Cada cambio en `main` que supera los tests del CI se publica solo.
Los pasos están en **[docs/DESPLIEGUE.md](docs/DESPLIEGUE.md)**.

## Configuración

Toda la configuración sensible se pasa por **variables de entorno** (en local, en el archivo `.env`):

| Variable | ¿Obligatoria? | Para qué |
|----------|---------------|----------|
| `JWT_SECRET` | Sí | Clave para firmar los tokens (mín. 32 caracteres: `openssl rand -base64 48`) |
| `JWT_EXPIRACION` | No | Duración del token (por defecto `1h`) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | No | Administrador que se crea al arrancar si no existe |
| `POSTGRES_*` | En local | Base de datos de `docker-compose` |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | En `prod` | Conexión a PostgreSQL |
| `MAIL_HOST` / `MAIL_PORT` | No | Servidor de correo (por defecto `localhost:1025`, Mailpit) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_SMTP_AUTH` / `MAIL_STARTTLS` | No | Credenciales de un SMTP real |
| `NOTIFICACIONES_HABILITADAS` | No | `false` = los correos solo se escriben en el log (en `prod` es `false` por defecto) |
| `NOTIFICACIONES_URL_BASE` | No | URL pública de la app, para los enlaces de los correos |
| `RESERVAS_ZONA_HORARIA` | No | Zona horaria del coworking (por defecto `America/Guayaquil`) |

## Estructura del código

El código se organiza **por funcionalidad** (todo lo de "reservas" junto), no por tipo de clase:

```
src/main/java/com/ramses/reservas/
├── auth/          Login, registro, JWT y configuración de seguridad
├── usuario/       Usuarios y administrador inicial
├── recurso/       Espacios: CRUD y horario de atención
├── reserva/       Lógica central: crear, cancelar, disponibilidad, eventos
├── notificacion/  Correos, recordatorios y tareas programadas
├── web/           Controllers de las páginas (Thymeleaf) y formularios
└── common/        Excepciones, manejo de errores, configuración y utilidades

src/main/resources/
├── db/migration/  Migraciones Flyway (V1 ... V6): historial versionado de la base de datos
├── templates/     Páginas HTML (Thymeleaf) y plantilla de correo
├── static/        CSS y JavaScript propios
└── application*.yml  Configuración por perfil
```

Convenciones: nombres de dominio en español (`Reserva`, `Recurso`) y términos técnicos en inglés (`Service`,
`Controller`, `Repository`). Los controllers nunca devuelven entidades, solo DTOs (`record` de Java).
