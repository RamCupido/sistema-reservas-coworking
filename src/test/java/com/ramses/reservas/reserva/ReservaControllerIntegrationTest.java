package com.ramses.reservas.reserva;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramses.reservas.IntegracionTest;
import com.ramses.reservas.LimpiadorBaseDatos;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Usa fechas relativas a hoy (siempre en el futuro) para no depender del día en que corre. */
@IntegracionTest
class ReservaControllerIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    private static final String RESERVAS = "/api/v1/reservas";
    private static final String ANA = "ana@mail.com";
    private static final String BETO = "beto@mail.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LimpiadorBaseDatos limpiadorBaseDatos;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private RecursoRepository recursoRepository;
    @Autowired
    private ReservaRepository reservaRepository;

    private Recurso sala;
    private final LocalDate dia = LocalDate.now(ZONA).plusDays(3);

    @BeforeEach
    void setUp() {
        limpiadorBaseDatos.limpiar();
        usuarioRepository.save(new Usuario("Ana", ANA, "x", Rol.CLIENTE));
        usuarioRepository.save(new Usuario("Beto", BETO, "x", Rol.CLIENTE));
        usuarioRepository.save(new Usuario("Admin", "admin@mail.com", "x", Rol.ADMIN));
        sala = recursoRepository.save(Recurso.builder().nombre("Sala Andes").tipo(TipoRecurso.SALA_REUNIONES)
                .capacidad(8).activo(true)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build());
    }

    // --- helpers ---

    private static JwtRequestPostProcessor cliente(String email) {
        return jwt().jwt(j -> j.subject(email)).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin@mail.com")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private ResultActions reservar(JwtRequestPostProcessor usuario, Long recursoId, String inicio, String fin)
            throws Exception {
        String body = """
                {"recursoId": %d, "fechaInicio": "%sT%s", "fechaFin": "%sT%s"}
                """.formatted(recursoId, dia, inicio, dia, fin);
        return mockMvc.perform(post(RESERVAS).with(usuario).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long crearReserva(String email, String inicio, String fin) throws Exception {
        String json = reservar(cliente(email), sala.getId(), inicio, fin)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    /** Inserta directamente (sin pasar por las reglas del service), p. ej. para una reserva que empieza en 1 h. */
    private Reserva insertarDirecto(String email, Instant inicio, Duration duracion) {
        Usuario usuario = usuarioRepository.findByEmail(email).orElseThrow();
        return reservaRepository.saveAndFlush(
                new Reserva(usuario, sala, inicio, inicio.plus(duracion)));
    }

    @Nested
    class Crear {

        @Test
        void reservaValidaDevuelve201ConLocation() throws Exception {
            reservar(cliente(ANA), sala.getId(), "10:00", "11:30")
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString(RESERVAS + "/")))
                    .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                    .andExpect(jsonPath("$.precioTotal").doesNotExist()) // no hay pagos
                    .andExpect(jsonPath("$.fechaInicio").value(dia + "T10:00"))
                    .andExpect(jsonPath("$.fechaFin").value(dia + "T11:30"))
                    .andExpect(jsonPath("$.recurso.nombre").value("Sala Andes"))
                    .andExpect(jsonPath("$.usuario.email").value(ANA));
        }

        @Test
        void sinTokenDevuelve401() throws Exception {
            mockMvc.perform(post(RESERVAS).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void reservaSolapadaDevuelve409() throws Exception {
            crearReserva(ANA, "10:00", "11:00");

            reservar(cliente(BETO), sala.getId(), "10:30", "11:30")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Horario no disponible"));
        }

        @Test
        void reservasContiguasSonValidas() throws Exception {
            crearReserva(ANA, "10:00", "11:00");

            reservar(cliente(BETO), sala.getId(), "11:00", "12:00").andExpect(status().isCreated());
            reservar(cliente(BETO), sala.getId(), "09:00", "10:00").andExpect(status().isCreated());
        }

        @Test
        void unHorarioCanceladoVuelveAQuedarLibre() throws Exception {
            long id = crearReserva(ANA, "10:00", "11:00");
            mockMvc.perform(patch(RESERVAS + "/{id}/cancelar", id).with(cliente(ANA))).andExpect(status().isOk());

            reservar(cliente(BETO), sala.getId(), "10:00", "11:00").andExpect(status().isCreated());
        }

        @Test
        void fueraDelHorarioDeAtencionDevuelve400() throws Exception {
            reservar(cliente(ANA), sala.getId(), "19:30", "20:30")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Reserva inválida"));
        }

        @Test
        void enElPasadoDevuelve400() throws Exception {
            LocalDate ayer = LocalDate.now(ZONA).minusDays(1);
            String body = """
                    {"recursoId": %d, "fechaInicio": "%sT10:00", "fechaFin": "%sT11:00"}
                    """.formatted(sala.getId(), ayer, ayer);

            mockMvc.perform(post(RESERVAS).with(cliente(ANA)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", containsString("pasado")));
        }

        @Test
        void recursoInexistenteDevuelve404() throws Exception {
            reservar(cliente(ANA), 999_999L, "10:00", "11:00").andExpect(status().isNotFound());
        }

        @Test
        void camposObligatoriosDevuelven400() throws Exception {
            mockMvc.perform(post(RESERVAS).with(cliente(ANA)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.recursoId").exists())
                    .andExpect(jsonPath("$.errores.fechaInicio").exists());
        }
    }

    @Nested
    class Consultar {

        @Test
        void misReservasSoloIncluyeLasPropiasYFiltraPorEstado() throws Exception {
            long cancelada = crearReserva(ANA, "08:00", "09:00");
            crearReserva(ANA, "10:00", "11:00");
            crearReserva(BETO, "12:00", "13:00");
            mockMvc.perform(patch(RESERVAS + "/{id}/cancelar", cancelada).with(cliente(ANA)));

            mockMvc.perform(get(RESERVAS + "/mias").with(cliente(ANA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    // más recientes primero
                    .andExpect(jsonPath("$.content[0].fechaInicio").value(dia + "T10:00"));

            mockMvc.perform(get(RESERVAS + "/mias").param("estado", "CONFIRMADA").with(cliente(ANA)))
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        void reservaAjenaDevuelve404PeroElAdminLaVe() throws Exception {
            long id = crearReserva(ANA, "10:00", "11:00");

            mockMvc.perform(get(RESERVAS + "/{id}", id).with(cliente(BETO))).andExpect(status().isNotFound());
            mockMvc.perform(get(RESERVAS + "/{id}", id).with(cliente(ANA))).andExpect(status().isOk());
            mockMvc.perform(get(RESERVAS + "/{id}", id).with(admin())).andExpect(status().isOk());
        }
    }

    @Nested
    class Cancelar {

        @Test
        void clienteCancelaSuReserva() throws Exception {
            long id = crearReserva(ANA, "10:00", "11:00");

            mockMvc.perform(patch(RESERVAS + "/{id}/cancelar", id).with(cliente(ANA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CANCELADA"));
        }

        @Test
        void cancelarReservaAjenaDevuelve404() throws Exception {
            long id = crearReserva(ANA, "10:00", "11:00");

            mockMvc.perform(patch(RESERVAS + "/{id}/cancelar", id).with(cliente(BETO)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void cancelarDosVecesDevuelve409() throws Exception {
            long id = crearReserva(ANA, "10:00", "11:00");
            mockMvc.perform(patch(RESERVAS + "/{id}/cancelar", id).with(cliente(ANA)));

            mockMvc.perform(patch(RESERVAS + "/{id}/cancelar", id).with(cliente(ANA)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Reserva no cancelable"));
        }

        @Test
        void conMenosDeDosHorasElClienteNoPuedeCancelarPeroElAdminSi() throws Exception {
            Instant enUnaHora = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MINUTES);
            Reserva reserva = insertarDirecto(ANA, enUnaHora, Duration.ofHours(1));

            mockMvc.perform(patch(RESERVAS + "/{id}/cancelar", reserva.getId()).with(cliente(ANA)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Cancelación fuera de plazo"));
            mockMvc.perform(patch(RESERVAS + "/{id}/cancelar", reserva.getId()).with(admin()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class Disponibilidad {

        @Test
        void esPublicaYMarcaLosBloquesOcupados() throws Exception {
            crearReserva(ANA, "10:00", "11:00");

            mockMvc.perform(get("/api/v1/recursos/{id}/disponibilidad", sala.getId()).param("fecha", dia.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bloques", hasSize(24))) // 08:00-20:00
                    .andExpect(jsonPath("$.bloques[3].inicio").value("09:30"))
                    .andExpect(jsonPath("$.bloques[3].disponible").value(true))
                    .andExpect(jsonPath("$.bloques[4].inicio").value("10:00"))
                    .andExpect(jsonPath("$.bloques[4].disponible").value(false))
                    .andExpect(jsonPath("$.bloques[5].disponible").value(false))
                    .andExpect(jsonPath("$.bloques[6].disponible").value(true));
        }

        @Test
        void sinFechaDevuelve400() throws Exception {
            mockMvc.perform(get("/api/v1/recursos/{id}/disponibilidad", sala.getId()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void recursoInexistenteDevuelve404() throws Exception {
            mockMvc.perform(get("/api/v1/recursos/{id}/disponibilidad", 999_999).param("fecha", dia.toString()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Administracion {

        @Test
        void clienteNoPuedeListarTodasLasReservas() throws Exception {
            mockMvc.perform(get("/api/v1/admin/reservas").with(cliente(ANA))).andExpect(status().isForbidden());
        }

        @Test
        void adminListaTodasConFiltros() throws Exception {
            crearReserva(ANA, "10:00", "11:00");
            long cancelada = crearReserva(BETO, "12:00", "13:00");
            mockMvc.perform(patch(RESERVAS + "/{id}/cancelar", cancelada).with(cliente(BETO)));

            mockMvc.perform(get("/api/v1/admin/reservas").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
            mockMvc.perform(get("/api/v1/admin/reservas").param("estado", "CANCELADA").with(admin()))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].usuario.email").value(BETO));
            mockMvc.perform(get("/api/v1/admin/reservas")
                            .param("desde", dia.plusDays(1).toString()).with(admin()))
                    .andExpect(jsonPath("$.totalElements").value(0));
            mockMvc.perform(get("/api/v1/admin/reservas")
                            .param("desde", dia.toString()).param("hasta", dia.toString()).with(admin()))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void desdePosteriorAHastaDevuelve400() throws Exception {
            mockMvc.perform(get("/api/v1/admin/reservas")
                            .param("desde", "2026-12-31").param("hasta", "2026-12-01").with(admin()))
                    .andExpect(status().isBadRequest());
        }
    }
}
