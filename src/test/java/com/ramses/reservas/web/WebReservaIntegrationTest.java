package com.ramses.reservas.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.ramses.reservas.IntegracionTest;
import com.ramses.reservas.LimpiadorBaseDatos;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.reserva.EstadoReserva;
import com.ramses.reservas.reserva.ReservaRepository;
import com.ramses.reservas.reserva.ReservaService;
import com.ramses.reservas.reserva.dto.CrearReservaRequest;
import com.ramses.reservas.reserva.dto.DisponibilidadResponse;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Flujo del cliente en las páginas: agenda, reservar, mis reservas, cancelar y registro. */
@IntegracionTest
class WebReservaIntegrationTest {

    private static final String ANA = "ana@mail.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LimpiadorBaseDatos limpiadorBaseDatos;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private RecursoRepository recursoRepository;
    @Autowired
    private ReservaRepository reservaRepository;
    @Autowired
    private ReservaService reservaService;

    private Recurso sala;
    private final LocalDate dia = LocalDate.now(ZoneId.of("America/Guayaquil")).plusDays(5);

    @BeforeEach
    void setUp() {
        limpiadorBaseDatos.limpiar();
        usuarioRepository.save(new Usuario("Ana", ANA, "hash", Rol.CLIENTE));
        usuarioRepository.save(new Usuario("Beto", "beto@mail.com", "hash", Rol.CLIENTE));
        sala = recursoRepository.save(Recurso.builder().nombre("Sala Andes").tipo(TipoRecurso.SALA_REUNIONES)
                .capacidad(8).activo(true)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build());
    }

    private ReservaResponse reservaDe(String email, int horaInicio, int horaFin) {
        return reservaService.crear(new CrearReservaRequest(sala.getId(), dia.atTime(horaInicio, 0),
                dia.atTime(horaFin, 0)), email);
    }

    private MockHttpServletRequestBuilder formularioReserva(String inicio, String fin) {
        return post("/reservas").with(user(ANA)).with(csrf())
                .param("recursoId", sala.getId().toString())
                .param("fecha", dia.toString())
                .param("horaInicio", inicio)
                .param("horaFin", fin);
    }

    private String agenda() {
        return "/recursos/" + sala.getId() + "?fecha=" + dia;
    }

    @Nested
    class Agenda {

        @Test
        void muestraLosBloquesDelDia() throws Exception {
            reservaDe("beto@mail.com", 10, 11);

            var resultado = mockMvc.perform(get("/recursos/{id}", sala.getId()).param("fecha", dia.toString()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("recurso"))
                    .andReturn();

            var disponibilidad = (DisponibilidadResponse) resultado.getModelAndView().getModel().get("disponibilidad");
            assertThat(disponibilidad.bloques()).hasSize(24); // 08:00-20:00
            assertThat(disponibilidad.bloques().stream().filter(b -> !b.disponible())).hasSize(2);
        }

        @Test
        void unAnonimoVeLaAgendaPeroNoElFormulario() throws Exception {
            mockMvc.perform(get("/recursos/{id}", sala.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Necesitas una cuenta")))
                    .andExpect(content().string(not(containsString("form-reserva"))));
        }

        @Test
        void unaFechaPasadaSeLlevaAHoy() throws Exception {
            var resultado = mockMvc.perform(get("/recursos/{id}", sala.getId()).param("fecha", "2020-01-01"))
                    .andExpect(status().isOk())
                    .andReturn();

            var disponibilidad = (DisponibilidadResponse) resultado.getModelAndView().getModel().get("disponibilidad");
            assertThat(disponibilidad.fecha()).isEqualTo(resultado.getModelAndView().getModel().get("hoy"));
        }

        @Test
        void unaFechaMalFormadaEsUn400() throws Exception {
            mockMvc.perform(get("/recursos/{id}", sala.getId()).param("fecha", "mañana"))
                    .andExpect(status().isBadRequest())
                    .andExpect(view().name("error"));
        }
    }

    @Nested
    class Reservar {

        @Test
        void reservaDesdeElFormularioYRedirigeAlCalendarioDeEseMes() throws Exception {
            mockMvc.perform(formularioReserva("10:00", "11:30"))
                    .andExpect(redirectedUrl("/mis-reservas?mes=" + YearMonth.from(dia)))
                    .andExpect(flash().attributeExists("reservaNueva"))
                    .andExpect(flash().attribute("exito", containsString("Reserva confirmada: Sala Andes")));

            assertThat(reservaRepository.count()).isEqualTo(1);
        }

        @Test
        void unHorarioOcupadoVuelveALaAgendaConElError() throws Exception {
            reservaDe("beto@mail.com", 10, 11);

            mockMvc.perform(formularioReserva("10:30", "11:30"))
                    .andExpect(redirectedUrl(agenda()))
                    .andExpect(flash().attribute("error", containsString("se solapa")));

            assertThat(reservaRepository.count()).isEqualTo(1);
        }

        @Test
        void fueraDelHorarioDeAtencionVuelveConElMensajeDelService() throws Exception {
            mockMvc.perform(formularioReserva("19:30", "20:30"))
                    .andExpect(redirectedUrl(agenda()))
                    .andExpect(flash().attribute("error", containsString("horario de atención")));
        }

        @Test
        void unaHoraMalFormadaMuestraUnMensajeGenerico() throws Exception {
            mockMvc.perform(formularioReserva("diez", "11:00"))
                    .andExpect(redirectedUrl(agenda()))
                    .andExpect(flash().attribute("error", "Elige un horario válido"));
        }

        @Test
        void sinHorasSeUsaElMensajeDeValidacionDeLaApi() throws Exception {
            mockMvc.perform(formularioReserva("", ""))
                    .andExpect(redirectedUrl(agenda()))
                    .andExpect(flash().attribute("error", containsString("obligatoria")));
        }
    }

    @Nested
    class MisReservas {

        @Test
        void listaSoloLasPropiasYMarcaLasCancelables() throws Exception {
            ReservaResponse propia = reservaDe(ANA, 10, 11);
            reservaDe("beto@mail.com", 12, 13);

            var resultado = mockMvc.perform(get("/mis-reservas").param("vista", "lista").with(user(ANA)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("cancelables", Set.of(propia.id())))
                    .andReturn();

            var pagina = (com.ramses.reservas.common.dto.PaginaResponse<?>) resultado.getModelAndView().getModel()
                    .get("pagina");
            assertThat(pagina.totalElements()).isEqualTo(1);
        }

        @Test
        void porDefectoMuestraElCalendarioDelMesConLasReservasPropias() throws Exception {
            ReservaResponse propia = reservaDe(ANA, 10, 11);
            reservaDe("beto@mail.com", 12, 13);

            var resultado = mockMvc.perform(get("/mis-reservas").param("mes", YearMonth.from(dia).toString())
                            .with(user(ANA)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("vista", "calendario"))
                    .andExpect(model().attribute("cancelables", Set.of(propia.id())))
                    .andExpect(content().string(containsString("dia-" + dia)))
                    .andReturn();

            var calendario = (CalendarioMensual) resultado.getModelAndView().getModel().get("calendario");
            assertThat(calendario.mes()).isEqualTo(YearMonth.from(dia));
            assertThat(calendario.totalReservasDelMes()).isEqualTo(1);
            assertThat(calendario.diasConReservas().getFirst().fecha()).isEqualTo(dia);
        }

        @Test
        void unMesSinReservasMuestraElCalendarioVacio() throws Exception {
            reservaDe(ANA, 10, 11);

            var resultado = mockMvc.perform(get("/mis-reservas").param("mes", YearMonth.from(dia).plusMonths(3).toString())
                            .with(user(ANA)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("No tienes reservas este mes")))
                    .andReturn();

            var calendario = (CalendarioMensual) resultado.getModelAndView().getModel().get("calendario");
            assertThat(calendario.totalReservasDelMes()).isZero();
        }

        @Test
        void unMesMalFormadoEsUn400() throws Exception {
            mockMvc.perform(get("/mis-reservas").param("mes", "noviembre").with(user(ANA)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void cancelarDesdeElCalendarioVuelveAlMismoMes() throws Exception {
            ReservaResponse reserva = reservaDe(ANA, 10, 11);
            String mes = YearMonth.from(dia).toString();

            mockMvc.perform(post("/mis-reservas/{id}/cancelar", reserva.id()).param("mes", mes)
                            .with(user(ANA)).with(csrf()))
                    .andExpect(redirectedUrl("/mis-reservas?mes=" + mes));
        }

        @Test
        void cancelarDesdeLaListaVuelveALaLista() throws Exception {
            ReservaResponse reserva = reservaDe(ANA, 10, 11);

            mockMvc.perform(post("/mis-reservas/{id}/cancelar", reserva.id()).param("vista", "lista")
                            .param("page", "0").with(user(ANA)).with(csrf()))
                    .andExpect(redirectedUrl("/mis-reservas?vista=lista&page=0"));
        }

        @Test
        void cancelaUnaReservaPropia() throws Exception {
            ReservaResponse reserva = reservaDe(ANA, 10, 11);

            mockMvc.perform(post("/mis-reservas/{id}/cancelar", reserva.id()).with(user(ANA)).with(csrf()))
                    .andExpect(redirectedUrl("/mis-reservas"))
                    .andExpect(flash().attribute("exito", "Reserva cancelada"));

            assertThat(reservaRepository.findById(reserva.id()).orElseThrow().getEstado())
                    .isEqualTo(EstadoReserva.CANCELADA);
        }

        @Test
        void cancelarDosVecesMuestraElErrorComoAlerta() throws Exception {
            ReservaResponse reserva = reservaDe(ANA, 10, 11);
            reservaService.cancelar(reserva.id(), ANA, false);

            mockMvc.perform(post("/mis-reservas/{id}/cancelar", reserva.id()).with(user(ANA)).with(csrf()))
                    .andExpect(redirectedUrl("/mis-reservas"))
                    .andExpect(flash().attribute("error", "La reserva ya está cancelada"));
        }

        @Test
        void unaReservaAjenaEsUn404() throws Exception {
            ReservaResponse deBeto = reservaDe("beto@mail.com", 10, 11);

            mockMvc.perform(post("/mis-reservas/{id}/cancelar", deBeto.id()).with(user(ANA)).with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));

            assertThat(reservaRepository.findById(deBeto.id()).orElseThrow().getEstado())
                    .isEqualTo(EstadoReserva.CONFIRMADA);
        }
    }

    @Nested
    class Registro {

        @Test
        void registroValidoRedirigeAlLogin() throws Exception {
            mockMvc.perform(post("/registro").with(csrf())
                            .param("nombre", "Carla")
                            .param("email", "Carla@Mail.com")
                            .param("password", "secreta123")
                            .param("confirmarPassword", "secreta123"))
                    .andExpect(redirectedUrl("/login?registrado"));

            assertThat(usuarioRepository.findByEmail("carla@mail.com")).isPresent();
        }

        @Test
        void erroresDeValidacionVuelvenAlFormularioSinDevolverLaContrasena() throws Exception {
            mockMvc.perform(post("/registro").with(csrf())
                            .param("nombre", "")
                            .param("email", "no-es-email")
                            .param("password", "corta")
                            .param("confirmarPassword", "otra"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("registro"))
                    .andExpect(model().attributeHasFieldErrors("registro",
                            "nombre", "email", "password", "confirmarPassword"))
                    .andExpect(content().string(not(containsString("value=\"corta\""))));
        }

        @Test
        void unEmailYaRegistradoEsUnErrorDelCampo() throws Exception {
            mockMvc.perform(post("/registro").with(csrf())
                            .param("nombre", "Otra Ana")
                            .param("email", ANA)
                            .param("password", "secreta123")
                            .param("confirmarPassword", "secreta123"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrorCode("registro", "email", "duplicado"));
        }
    }
}
