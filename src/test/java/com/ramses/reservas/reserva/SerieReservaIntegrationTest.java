package com.ramses.reservas.reserva;

import static com.ramses.reservas.CorreoFalso.asunto;
import static com.ramses.reservas.CorreoFalso.html;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.ramses.reservas.CorreoFalso;
import com.ramses.reservas.IntegracionTest;
import com.ramses.reservas.LimpiadorBaseDatos;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.reserva.dto.CrearReservaRequest;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Reservas semanales de punta a punta: API, PostgreSQL real (constraint incluido) y correos. */
@IntegracionTest
class SerieReservaIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    private static final String API = "/api/v1/reservas";
    private static final String ANA = "ana@mail.com";
    private static final String BETO = "beto@mail.com";
    private static final DateTimeFormatter FECHA_CORREO =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"));

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
    @Autowired
    private SerieReservaRepository serieRepository;
    @Autowired
    private ReservaService reservaService;
    @Autowired
    private CorreoFalso correo;

    private Recurso sala;
    /** Siempre en el futuro, para no depender del día en que corre el test. */
    private final LocalDate dia = LocalDate.now(ZONA).plusDays(3);

    @BeforeEach
    void setUp() {
        limpiadorBaseDatos.limpiar();
        correo.reiniciar();
        usuarioRepository.save(new Usuario("Ana", ANA, "x", Rol.CLIENTE));
        usuarioRepository.save(new Usuario("Beto", BETO, "x", Rol.CLIENTE));
        usuarioRepository.save(new Usuario("Admin", "admin@mail.com", "x", Rol.ADMIN));
        sala = recursoRepository.save(Recurso.builder().nombre("Sala Andes").tipo(TipoRecurso.SALA_REUNIONES)
                .capacidad(8).activo(true)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build());
    }

    private static JwtRequestPostProcessor cliente(String email) {
        return jwt().jwt(j -> j.subject(email)).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private ResultActions crearSerie(JwtRequestPostProcessor usuario, int semanas) throws Exception {
        String body = """
                {"recursoId": %d, "fechaInicio": "%sT10:00", "fechaFin": "%sT11:00", "semanas": %d}
                """.formatted(sala.getId(), dia, dia, semanas);
        return mockMvc.perform(post(API + "/recurrentes").with(usuario)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long crearSerieDeAna(int semanas) throws Exception {
        String json = crearSerie(cliente(ANA), semanas).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Nested
    class Crear {

        @Test
        void creaTodasLasSemanasYDevuelve201ConLocation() throws Exception {
            crearSerie(cliente(ANA), 3)
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString(API + "/series/")))
                    .andExpect(jsonPath("$.semanas").value(3))
                    .andExpect(jsonPath("$.recurso.nombre").value("Sala Andes"))
                    .andExpect(jsonPath("$.reservas", hasSize(3)))
                    .andExpect(jsonPath("$.reservas[0].fechaInicio").value(dia + "T10:00"))
                    .andExpect(jsonPath("$.reservas[1].fechaInicio").value(dia.plusWeeks(1) + "T10:00"))
                    .andExpect(jsonPath("$.reservas[2].fechaInicio").value(dia.plusWeeks(2) + "T10:00"))
                    .andExpect(jsonPath("$.reservas[0].estado").value("CONFIRMADA"))
                    .andExpect(jsonPath("$.reservas[0].serieId").isNumber());

            assertThat(reservaRepository.count()).isEqualTo(3);
            assertThat(serieRepository.count()).isEqualTo(1);
        }

        @Test
        void siUnaSemanaEstaOcupadaResponde409ConLasFechasYNoGuardaNada() throws Exception {
            LocalDate ocupado = dia.plusWeeks(1);
            reservaService.crear(new CrearReservaRequest(sala.getId(), ocupado.atTime(10, 30),
                    ocupado.atTime(11, 30)), BETO);

            crearSerie(cliente(ANA), 4)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Horario no disponible"))
                    .andExpect(jsonPath("$.fechasEnConflicto", hasSize(1)))
                    .andExpect(jsonPath("$.fechasEnConflicto[0]").value(ocupado.toString()))
                    .andExpect(jsonPath("$.detail", containsString("No se ha creado ninguna")));

            assertThat(reservaRepository.count()).isEqualTo(1);   // solo la de Beto
            assertThat(serieRepository.count()).isZero();
        }

        @Test
        void masDe12SemanasEsInvalido() throws Exception {
            crearSerie(cliente(ANA), 13)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.semanas").value("Una reserva semanal dura entre 2 y 12 semanas"));
        }

        @Test
        void sinTokenResponde401() throws Exception {
            mockMvc.perform(post(API + "/recurrentes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void enviaUnSoloCorreoConTodasLasFechas() throws Exception {
            crearSerieDeAna(3);

            List<MimeMessage> recibidos = correo.esperarCorreosPara(ANA, 1);
            correo.darMargenAlEnvioAsincrono();
            assertThat(correo.enviadosA(ANA)).hasSize(1);   // uno, no tres
            assertThat(asunto(recibidos.getFirst())).startsWith("Reserva semanal confirmada: Sala Andes, 3 fechas");
            assertThat(html(recibidos.getFirst()))
                    .contains(dia.format(FECHA_CORREO), dia.plusWeeks(1).format(FECHA_CORREO),
                            dia.plusWeeks(2).format(FECHA_CORREO))
                    .contains("10:00 – 11:00");
        }
    }

    @Nested
    class ConsultarYCancelar {

        @Test
        void elDuenoVeSuSerieYOtroClienteRecibe404() throws Exception {
            long serieId = crearSerieDeAna(2);

            mockMvc.perform(get(API + "/series/{id}", serieId).with(cliente(ANA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservas", hasSize(2)));
            mockMvc.perform(get(API + "/series/{id}", serieId).with(cliente(BETO)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(patch(API + "/series/{id}/cancelar", serieId).with(cliente(BETO)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void cancelarLasRestantesAnulaTodasYEnviaUnSoloCorreo() throws Exception {
            long serieId = crearSerieDeAna(3);
            correo.esperarCorreosPara(ANA, 1);

            mockMvc.perform(patch(API + "/series/{id}/cancelar", serieId).with(cliente(ANA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservas[0].estado").value("CANCELADA"))
                    .andExpect(jsonPath("$.reservas[2].estado").value("CANCELADA"));

            List<MimeMessage> recibidos = correo.esperarCorreosPara(ANA, 2);
            correo.darMargenAlEnvioAsincrono();
            assertThat(correo.enviadosA(ANA)).hasSize(2);   // confirmación + una cancelación
            assertThat(asunto(recibidos.get(1))).startsWith("Reserva semanal cancelada: Sala Andes, 3 fechas");
        }

        @Test
        void elCorreoDeCancelacionSoloMencionaLasQueSeAcabanDeCancelar() throws Exception {
            long serieId = crearSerieDeAna(3);
            Reserva primera = reservaRepository.findBySerieIdOrderByFechaInicio(serieId).getFirst();
            reservaService.cancelar(primera.getId(), ANA, false);   // una sola fecha, por su cuenta
            correo.esperarCorreosPara(ANA, 2);

            mockMvc.perform(patch(API + "/series/{id}/cancelar", serieId).with(cliente(ANA)))
                    .andExpect(status().isOk());

            MimeMessage cancelacion = correo.esperarCorreosPara(ANA, 3).get(2);
            assertThat(asunto(cancelacion)).contains("2 fechas");
            assertThat(html(cancelacion))
                    .doesNotContain(dia.format(FECHA_CORREO))
                    .contains(dia.plusWeeks(1).format(FECHA_CORREO), dia.plusWeeks(2).format(FECHA_CORREO));
        }

        @Test
        void siYaNoQuedaNadaQueCancelarResponde409() throws Exception {
            long serieId = crearSerieDeAna(2);
            mockMvc.perform(patch(API + "/series/{id}/cancelar", serieId).with(cliente(ANA)))
                    .andExpect(status().isOk());

            // Mismo código que cancelar una reserva ya cancelada: el estado actual lo impide.
            mockMvc.perform(patch(API + "/series/{id}/cancelar", serieId).with(cliente(ANA)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail", containsString("No quedan fechas")));
        }

        @Test
        void unaFechaCanceladaDeLaSerieDejaElHuecoLibreParaOtros() throws Exception {
            long serieId = crearSerieDeAna(2);
            Reserva segunda = reservaRepository.findBySerieIdOrderByFechaInicio(serieId).get(1);
            reservaService.cancelar(segunda.getId(), ANA, false);

            reservaService.crear(new CrearReservaRequest(sala.getId(), dia.plusWeeks(1).atTime(10, 0),
                    dia.plusWeeks(1).atTime(11, 0)), BETO);

            assertThat(reservaRepository.count()).isEqualTo(3);
        }
    }
}
