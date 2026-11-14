package com.ramses.reservas.common.exception;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ramses.reservas.IntegracionTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica que todos los errores, incluso los inesperados, salen como ProblemDetail. */
@IntegracionTest
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void errorInesperadoDevuelve500SinFiltrarDetallesInternos() throws Exception {
        mockMvc.perform(get(ErroresTestController.RUTA_ERROR).with(jwt()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(content().string(not(containsString(ErroresTestController.MENSAJE_INTERNO))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void jsonMalFormadoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void parametroDeTipoIncorrectoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/recursos").param("tipo", "PISCINA"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void idNoNumericoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/recursos/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordenarPorPropiedadInexistenteDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/recursos").param("sort", "noExiste"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
    }

    @Test
    void parametroFueraDeRangoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/recursos").param("capacidadMinima", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metodoNoPermitidoDevuelve405() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/me").with(jwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
