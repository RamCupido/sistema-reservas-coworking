package com.ramses.reservas.recurso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ramses.reservas.IntegracionTest;
import com.ramses.reservas.LimpiadorBaseDatos;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * jwt() de spring-security-test simula un usuario autenticado con un JWT ya validado.
 * El flujo real de login/firma ya lo cubre AuthControllerIntegrationTest; aquí nos
 * centramos en la autorización y en el CRUD.
 */
@IntegracionTest
class RecursoControllerIntegrationTest {

    private static final String URL = "/api/v1/recursos";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LimpiadorBaseDatos limpiadorBaseDatos;
    @Autowired
    private RecursoRepository recursoRepository;

    @BeforeEach
    void limpiar() {
        limpiadorBaseDatos.limpiar();
    }

    private static JwtRequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin@mail.com")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static JwtRequestPostProcessor cliente() {
        return jwt().jwt(j -> j.subject("ana@mail.com")).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private Recurso guardar(String nombre, TipoRecurso tipo, int capacidad, boolean activo) {
        return recursoRepository.save(Recurso.builder().nombre(nombre).descripcion("desc").tipo(tipo)
                .capacidad(capacidad).activo(activo)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build());
    }

    private static String json(String nombre, String apertura, String cierre) {
        return """
                {"nombre": "%s", "descripcion": "Sala luminosa", "tipo": "SALA_REUNIONES",
                 "capacidad": 8, "horaApertura": "%s", "horaCierre": "%s"}
                """.formatted(nombre, apertura, cierre);
    }

    @Nested
    class LecturaPublica {

        @Test
        void listarSinTokenDevuelveSoloActivosOrdenadosPorNombre() throws Exception {
            guardar("Sala Zeta", TipoRecurso.SALA_REUNIONES, 6, true);
            guardar("Auditorio Central", TipoRecurso.AUDITORIO, 100, true);
            guardar("Sala Vieja", TipoRecurso.SALA_REUNIONES, 4, false);

            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].nombre").value("Auditorio Central"))
                    .andExpect(jsonPath("$.content[1].nombre").value("Sala Zeta"))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void incluirInactivosSoloFuncionaParaAdmin() throws Exception {
            guardar("Sala Activa", TipoRecurso.SALA_REUNIONES, 6, true);
            guardar("Sala Vieja", TipoRecurso.SALA_REUNIONES, 4, false);

            mockMvc.perform(get(URL).param("incluirInactivos", "true"))
                    .andExpect(jsonPath("$.totalElements").value(1));
            mockMvc.perform(get(URL).param("incluirInactivos", "true").with(admin()))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void filtraPorTipoYCapacidadMinima() throws Exception {
            guardar("Escritorio 1", TipoRecurso.ESCRITORIO, 1, true);
            guardar("Sala Chica", TipoRecurso.SALA_REUNIONES, 4, true);
            guardar("Sala Grande", TipoRecurso.SALA_REUNIONES, 12, true);

            mockMvc.perform(get(URL).param("tipo", "SALA_REUNIONES").param("capacidadMinima", "10"))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].nombre").value("Sala Grande"));
        }

        @Test
        void paginaLosResultados() throws Exception {
            for (int i = 1; i <= 5; i++) {
                guardar("Escritorio " + i, TipoRecurso.ESCRITORIO, 1, true);
            }

            mockMvc.perform(get(URL).param("page", "1").param("size", "2"))
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].nombre").value("Escritorio 3"))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3));
        }

        @Test
        void obtenerPorIdSinToken() throws Exception {
            Recurso recurso = guardar("Sala Andes", TipoRecurso.SALA_REUNIONES, 8, true);

            mockMvc.perform(get(URL + "/{id}", recurso.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Sala Andes"))
                    .andExpect(jsonPath("$.horaApertura").value("08:00"));
        }

        @Test
        void obtenerInexistenteDevuelve404() throws Exception {
            mockMvc.perform(get(URL + "/{id}", 999_999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
        }
    }

    @Nested
    class Escritura {

        @Test
        void crearSinTokenDevuelve401() throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json("Sala", "08:00", "20:00")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void crearComoClienteDevuelve403() throws Exception {
            mockMvc.perform(post(URL).with(cliente())
                            .contentType(MediaType.APPLICATION_JSON).content(json("Sala", "08:00", "20:00")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Acceso denegado"));
        }

        @Test
        void crearComoAdminDevuelve201ConLocation() throws Exception {
            mockMvc.perform(post(URL).with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json("Sala Andes", "08:00", "20:00")))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString(URL + "/")))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.activo").value(true))
                    .andExpect(jsonPath("$.precioPorHora").doesNotExist()); // no hay pagos
        }

        @Test
        void crearConDatosInvalidosDevuelve400() throws Exception {
            String body = """
                    {"nombre": "", "tipo": "SALA_REUNIONES", "capacidad": 0, "horaApertura": "08:00", "horaCierre": "20:00"}
                    """;
            mockMvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.nombre").exists())
                    .andExpect(jsonPath("$.errores.capacidad").exists());
        }

        @Test
        void crearConHorarioInvalidoDevuelve400() throws Exception {
            mockMvc.perform(post(URL).with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json("Sala", "20:00", "08:00")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Horario inválido"));
        }

        @Test
        void crearConNombreDuplicadoIgnorandoMayusculasDevuelve409() throws Exception {
            guardar("Sala Andes", TipoRecurso.SALA_REUNIONES, 8, true);

            mockMvc.perform(post(URL).with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json("SALA ANDES", "08:00", "20:00")))
                    .andExpect(status().isConflict());
        }

        @Test
        void actualizarComoAdminReemplazaElRecurso() throws Exception {
            Recurso recurso = guardar("Sala Andes", TipoRecurso.SALA_REUNIONES, 8, true);

            mockMvc.perform(put(URL + "/{id}", recurso.getId()).with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json("Sala Andes 2", "09:00", "18:30")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Sala Andes 2"))
                    .andExpect(jsonPath("$.horaCierre").value("18:30"));
        }

        @Test
        void actualizarInexistenteDevuelve404() throws Exception {
            mockMvc.perform(put(URL + "/{id}", 999_999).with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(json("Sala", "08:00", "20:00")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void eliminarHaceBorradoLogicoYElRecursoDesapareceDelCatalogo() throws Exception {
            Recurso recurso = guardar("Sala Andes", TipoRecurso.SALA_REUNIONES, 8, true);

            mockMvc.perform(delete(URL + "/{id}", recurso.getId()).with(admin()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(URL + "/{id}", recurso.getId())).andExpect(status().isNotFound());
            mockMvc.perform(get(URL)).andExpect(jsonPath("$.totalElements").value(0));
            // Sigue en la BD (historial intacto para las reservas futuras)
            assertThat(recursoRepository.findById(recurso.getId()))
                    .get().extracting(Recurso::isActivo).isEqualTo(false);
        }

        @Test
        void eliminarComoClienteDevuelve403() throws Exception {
            Recurso recurso = guardar("Sala Andes", TipoRecurso.SALA_REUNIONES, 8, true);

            mockMvc.perform(delete(URL + "/{id}", recurso.getId()).with(cliente()))
                    .andExpect(status().isForbidden());
        }
    }
}
