package com.ramses.reservas.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.ramses.reservas.IntegracionTest;
import com.ramses.reservas.LimpiadorBaseDatos;
import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.reserva.EstadoReserva;
import com.ramses.reservas.reserva.ReservaRepository;
import com.ramses.reservas.reserva.ReservaService;
import com.ramses.reservas.reserva.dto.CrearReservaRequest;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Panel de administración: CRUD de recursos y gestión de reservas. */
@IntegracionTest
class WebAdminIntegrationTest {

    private static final RequestPostProcessor ADMIN = user("admin@mail.com").roles("ADMIN");

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
        usuarioRepository.save(new Usuario("Ana", "ana@mail.com", "hash", Rol.CLIENTE));
        sala = recursoRepository.save(Recurso.builder().nombre("Sala Andes").tipo(TipoRecurso.SALA_REUNIONES)
                .capacidad(8).activo(true)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build());
    }

    private ReservaResponse reservaDeAna(int horaInicio, int horaFin) {
        return reservaService.crear(new CrearReservaRequest(sala.getId(), dia.atTime(horaInicio, 0),
                dia.atTime(horaFin, 0)), "ana@mail.com");
    }

    private static MockHttpServletRequestBuilder formularioRecurso(String url, String nombre, String apertura,
                                                                   String cierre) {
        return post(url).with(ADMIN).with(csrf())
                .param("nombre", nombre)
                .param("tipo", "OFICINA_PRIVADA")
                .param("capacidad", "4")
                .param("horaApertura", apertura)
                .param("horaCierre", cierre)
                .param("activo", "true");
    }

    private static Object modelo(MvcResult resultado, String atributo) {
        return resultado.getModelAndView().getModel().get(atributo);
    }

    @Nested
    class Recursos {

        @Test
        void creaUnRecursoDesdeElFormulario() throws Exception {
            mockMvc.perform(formularioRecurso("/admin/recursos", "Oficina Cotopaxi", "09:00", "18:00"))
                    .andExpect(redirectedUrl("/admin/recursos"))
                    .andExpect(flash().attribute("exito", "Recurso 'Oficina Cotopaxi' creado"));

            Recurso creado = recursoRepository.findAll().stream()
                    .filter(r -> r.getNombre().equals("Oficina Cotopaxi")).findFirst().orElseThrow();
            assertThat(creado.getHoraCierre()).isEqualTo(LocalTime.of(18, 0));
        }

        @Test
        void losErroresDeValidacionDeLaApiAparecenEnElFormulario() throws Exception {
            mockMvc.perform(post("/admin/recursos").with(ADMIN).with(csrf())
                            .param("nombre", "")
                            .param("capacidad", "0")
                            .param("horaApertura", "08:00")
                            .param("horaCierre", "20:00"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/recurso-form"))
                    .andExpect(model().attributeHasFieldErrors("recurso", "nombre", "tipo", "capacidad"));

            assertThat(recursoRepository.count()).isEqualTo(1);
        }

        @Test
        void unHorarioInvertidoEsUnErrorDelCampoCierre() throws Exception {
            mockMvc.perform(formularioRecurso("/admin/recursos", "Oficina Cotopaxi", "18:00", "09:00"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrorCode("recurso", "horaCierre", "horario"));
        }

        @Test
        void unNombreDuplicadoEsUnErrorDelCampoNombre() throws Exception {
            mockMvc.perform(formularioRecurso("/admin/recursos", "sala andes", "09:00", "18:00"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrorCode("recurso", "nombre", "duplicado"));
        }

        @Test
        void editaUnRecurso() throws Exception {
            mockMvc.perform(get("/admin/recursos/{id}/editar", sala.getId()).with(ADMIN))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("accion", "/admin/recursos/" + sala.getId()));

            mockMvc.perform(formularioRecurso("/admin/recursos/" + sala.getId(), "Sala Andes 2", "07:00", "21:00"))
                    .andExpect(redirectedUrl("/admin/recursos"));

            Recurso editado = recursoRepository.findById(sala.getId()).orElseThrow();
            assertThat(editado.getNombre()).isEqualTo("Sala Andes 2");
            assertThat(editado.getHoraApertura()).isEqualTo(LocalTime.of(7, 0));
        }

        @Test
        void desactivarAvisaDeLasReservasFuturasYLasMantiene() throws Exception {
            ReservaResponse reserva = reservaDeAna(10, 11);

            mockMvc.perform(post("/admin/recursos/{id}/desactivar", sala.getId()).with(ADMIN).with(csrf()))
                    .andExpect(redirectedUrl("/admin/recursos"))
                    .andExpect(flash().attribute("aviso", containsString("1 reserva futura")));

            assertThat(recursoRepository.findById(sala.getId()).orElseThrow().isActivo()).isFalse();
            assertThat(reservaRepository.findById(reserva.id()).orElseThrow().getEstado())
                    .isEqualTo(EstadoReserva.CONFIRMADA);
        }

        @Test
        void desactivarSinReservasFuturasNoAvisa() throws Exception {
            ReservaResponse cancelada = reservaDeAna(10, 11);
            reservaService.cancelar(cancelada.id(), "ana@mail.com", false);

            mockMvc.perform(post("/admin/recursos/{id}/desactivar", sala.getId()).with(ADMIN).with(csrf()))
                    .andExpect(flash().attributeExists("exito"))
                    .andExpect(flash().attribute("aviso", nullValue()));
        }
    }

    @Nested
    class Reservas {

        @Test
        void filtraPorEstado() throws Exception {
            reservaDeAna(10, 11);
            ReservaResponse cancelada = reservaDeAna(12, 13);
            reservaService.cancelar(cancelada.id(), "ana@mail.com", false);

            MvcResult resultado = mockMvc.perform(get("/admin/reservas").with(ADMIN).param("estado", "CANCELADA"))
                    .andExpect(status().isOk())
                    .andReturn();

            PaginaResponse<?> pagina = (PaginaResponse<?>) modelo(resultado, "pagina");
            assertThat(pagina.totalElements()).isEqualTo(1);
        }

        @Test
        void unRangoDeFechasInvertidoMuestraElErrorSinListado() throws Exception {
            mockMvc.perform(get("/admin/reservas").with(ADMIN)
                            .param("desde", dia.toString())
                            .param("hasta", dia.minusDays(1).toString()))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeExists("error"))
                    .andExpect(model().attributeDoesNotExist("pagina"));
        }

        @Test
        void elAdminCancelaYVuelveAlMismoListadoFiltrado() throws Exception {
            ReservaResponse reserva = reservaDeAna(10, 11);

            mockMvc.perform(post("/admin/reservas/{id}/cancelar", reserva.id()).with(ADMIN).with(csrf())
                            .param("estado", "CONFIRMADA")
                            .param("desde", dia.toString())
                            .param("page", "0"))
                    .andExpect(redirectedUrl("/admin/reservas?estado=CONFIRMADA&desde=" + dia + "&page=0"))
                    .andExpect(flash().attribute("exito", "Reserva #" + reserva.id() + " cancelada"));

            assertThat(reservaRepository.findById(reserva.id()).orElseThrow().getEstado())
                    .isEqualTo(EstadoReserva.CANCELADA);
        }
    }
}
