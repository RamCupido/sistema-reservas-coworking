package com.ramses.reservas.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
import com.ramses.reservas.reserva.SerieReservaService;
import com.ramses.reservas.reserva.dto.CrearReservaRequest;
import com.ramses.reservas.reserva.dto.CrearSerieRequest;
import com.ramses.reservas.reserva.dto.SerieResponse;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Reserva semanal desde las páginas: casilla "Repetir cada semana", calendario y "Cancelar las restantes". */
@IntegracionTest
class WebSerieReservaIntegrationTest {

    private static final String ANA = "ana@mail.com";
    private static final String BETO = "beto@mail.com";

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
    @Autowired
    private SerieReservaService serieService;

    private Recurso sala;
    private final LocalDate dia = LocalDate.now(ZoneId.of("America/Guayaquil")).plusDays(5);

    @BeforeEach
    void setUp() {
        limpiadorBaseDatos.limpiar();
        usuarioRepository.save(new Usuario("Ana", ANA, "hash", Rol.CLIENTE));
        usuarioRepository.save(new Usuario("Beto", BETO, "hash", Rol.CLIENTE));
        sala = recursoRepository.save(Recurso.builder().nombre("Sala Andes").tipo(TipoRecurso.SALA_REUNIONES)
                .capacidad(8).activo(true)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build());
    }

    private MockHttpServletRequestBuilder formularioSemanal(String semanas) {
        return formulario(semanas).param("repetir", "true");
    }

    /** Sin "repetir": así llega del navegador cuando la casilla no está marcada. */
    private MockHttpServletRequestBuilder formulario(String semanas) {
        return post("/reservas").with(user(ANA)).with(csrf())
                .param("recursoId", sala.getId().toString())
                .param("fecha", dia.toString())
                .param("horaInicio", "10:00")
                .param("horaFin", "11:00")
                .param("semanas", semanas);
    }

    private SerieResponse serieDeAna(int semanas) {
        return serieService.crear(new CrearSerieRequest(sala.getId(), dia.atTime(10, 0), dia.atTime(11, 0), semanas),
                ANA);
    }

    private String agenda() {
        return "/recursos/" + sala.getId() + "?fecha=" + dia;
    }

    @Nested
    class Reservar {

        @Test
        void laAgendaOfreceRepetirCadaSemana() throws Exception {
            mockMvc.perform(get("/recursos/{id}", sala.getId()).param("fecha", dia.toString()).with(user(ANA)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Repetir cada semana")))
                    .andExpect(content().string(containsString("12 semanas")));
        }

        @Test
        void creaLaSerieYAterrizaEnElCalendarioConElla() throws Exception {
            mockMvc.perform(formularioSemanal("3"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/mis-reservas?mes=" + YearMonth.from(dia)))
                    .andExpect(flash().attribute("exito", containsString("Reserva semanal confirmada: Sala Andes, 3 semanas")))
                    .andExpect(flash().attributeExists("serieNueva"));

            assertThat(reservaRepository.findAll()).hasSize(3)
                    .allSatisfy(r -> assertThat(r.getSerie()).isNotNull());
        }

        @Test
        void sinLaCasillaSeCreaUnaReservaSuelta() throws Exception {
            mockMvc.perform(formulario("3"))
                    .andExpect(flash().attributeExists("reservaNueva"));

            assertThat(reservaRepository.findAll()).singleElement()
                    .satisfies(r -> assertThat(r.getSerie()).isNull());
        }

        @Test
        void siUnaSemanaEstaOcupadaVuelveALaAgendaSinReservarNada() throws Exception {
            reservaService.crear(new CrearReservaRequest(sala.getId(), dia.plusWeeks(2).atTime(10, 0),
                    dia.plusWeeks(2).atTime(11, 0)), BETO);

            mockMvc.perform(formularioSemanal("4"))
                    .andExpect(redirectedUrl(agenda()))
                    .andExpect(flash().attribute("error", containsString("No se ha creado ninguna")));

            assertThat(reservaRepository.count()).isEqualTo(1);   // solo la de Beto
        }

        @Test
        void unNumeroDeSemanasFueraDeRangoSeAvisa() throws Exception {
            mockMvc.perform(formularioSemanal("20"))
                    .andExpect(redirectedUrl(agenda()))
                    .andExpect(flash().attribute("error", "Una reserva semanal dura entre 2 y 12 semanas"));

            assertThat(reservaRepository.count()).isZero();
        }
    }

    @Nested
    class MisReservas {

        @Test
        void elCalendarioMarcaLasReservasSemanalesYOfreceCancelarLasRestantes() throws Exception {
            SerieResponse serie = serieDeAna(2);

            mockMvc.perform(get("/mis-reservas").param("mes", YearMonth.from(dia).toString()).with(user(ANA)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Semanal")))
                    .andExpect(content().string(containsString("Cancelar esta fecha")))
                    .andExpect(content().string(containsString("/mis-reservas/series/" + serie.id() + "/cancelar")));
        }

        @Test
        void cancelarLasRestantesVuelveAlMismoMes() throws Exception {
            SerieResponse serie = serieDeAna(3);
            YearMonth mes = YearMonth.from(dia);

            mockMvc.perform(post("/mis-reservas/series/{id}/cancelar", serie.id()).with(user(ANA)).with(csrf())
                            .param("mes", mes.toString()))
                    .andExpect(redirectedUrl("/mis-reservas?mes=" + mes))
                    .andExpect(flash().attribute("exito", containsString("Reserva semanal cancelada")));

            assertThat(reservaRepository.findAll()).hasSize(3)
                    .allSatisfy(r -> assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELADA));
        }

        @Test
        void desdeLaListaVuelveALaLista() throws Exception {
            SerieResponse serie = serieDeAna(2);

            mockMvc.perform(post("/mis-reservas/series/{id}/cancelar", serie.id()).with(user(ANA)).with(csrf())
                            .param("vista", "lista").param("page", "0"))
                    .andExpect(redirectedUrl("/mis-reservas?vista=lista&page=0"));
        }

        @Test
        void siYaNoQuedaNadaSeAvisaComoError() throws Exception {
            SerieResponse serie = serieDeAna(2);
            serieService.cancelarRestantes(serie.id(), ANA, false);

            mockMvc.perform(post("/mis-reservas/series/{id}/cancelar", serie.id()).with(user(ANA)).with(csrf()))
                    .andExpect(flash().attribute("error", containsString("No quedan fechas")));
        }

        @Test
        void unaSerieAjenaEsUn404() throws Exception {
            SerieResponse serie = serieDeAna(2);

            mockMvc.perform(post("/mis-reservas/series/{id}/cancelar", serie.id()).with(user(BETO)).with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/404"));

            assertThat(reservaRepository.findAll())
                    .allSatisfy(r -> assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA));
        }

        @Test
        void sinCsrfNoSeCancela() throws Exception {
            SerieResponse serie = serieDeAna(2);

            mockMvc.perform(post("/mis-reservas/series/{id}/cancelar", serie.id()).with(user(ANA)))
                    .andExpect(status().isForbidden());
        }
    }
}
