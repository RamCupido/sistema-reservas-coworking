package com.ramses.reservas.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ramses.reservas.IntegracionTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegracionTest
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void laEspecificacionOpenApiEsPublicaYDocumentaLaSeguridad() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/recursos'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/recursos'].post.security[0].bearerAuth").exists());
    }

    @Test
    void swaggerUiEsPublico() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
