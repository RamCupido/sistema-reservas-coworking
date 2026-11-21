package com.ramses.reservas.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ramses.reservas.IntegracionTest;
import com.ramses.reservas.LimpiadorBaseDatos;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.reserva.ReservaService;
import com.ramses.reservas.reserva.dto.CrearReservaRequest;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** La portada cambia según haya sesión: presentación para visitantes, saludo para usuarios. */
@IntegracionTest
class WebPortadaIntegrationTest {

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
    private ReservaService reservaService;

    private Recurso sala;

    @BeforeEach
    void setUp() {
        limpiadorBaseDatos.limpiar();
        usuarioRepository.save(new Usuario("Ana", ANA, "hash", Rol.CLIENTE));
        sala = recursoRepository.save(Recurso.builder().nombre("Sala Andes").tipo(TipoRecurso.SALA_REUNIONES)
                .capacidad(8).activo(true).horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build());
    }

    @Test
    void unVisitanteVeLaPresentacionDelServicioYElCatalogo() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reserva tu espacio de trabajo")))
                .andExpect(content().string(containsString("Cómo funciona")))
                .andExpect(content().string(containsString("Crear cuenta gratis")))
                .andExpect(content().string(containsString("Sala Andes")))
                .andExpect(model().attributeDoesNotExist("nombreUsuario"));
    }

    @Test
    void unUsuarioConSesionVeSuSaludoSinLaPresentacion() throws Exception {
        mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("nombreUsuario", "Ana"))
                .andExpect(content().string(containsString("Hola, Ana")))
                .andExpect(content().string(containsString("No tienes reservas próximas")))
                .andExpect(content().string(not(containsString("Cómo funciona"))))
                .andExpect(content().string(containsString("Sala Andes")));
    }

    @Test
    void unUsuarioConReservasVeSuProximaReserva() throws Exception {
        LocalDate dia = LocalDate.now(ZoneId.of("America/Guayaquil")).plusDays(3);
        reservaService.crear(new CrearReservaRequest(sala.getId(), dia.atTime(10, 0), dia.atTime(11, 0)), ANA);

        mockMvc.perform(get("/").with(user(ANA)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("proxima"))
                .andExpect(content().string(containsString("Tu próxima reserva")));
    }

    @Test
    void unaSesionDeUnUsuarioYaBorradoNoRompeLaPortada() throws Exception {
        mockMvc.perform(get("/").with(user("borrado@mail.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hola")));
    }
}
