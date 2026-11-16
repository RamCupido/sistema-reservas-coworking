package com.ramses.reservas.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramses.reservas.IntegracionTest;
import com.ramses.reservas.LimpiadorBaseDatos;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Recorre la cadena completa: HTTP → filtros de seguridad → controller → service → PostgreSQL. */
@IntegracionTest
class AuthControllerIntegrationTest {

    private static final String REGISTRO = "/api/v1/auth/registro";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/usuarios/me";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LimpiadorBaseDatos limpiadorBaseDatos;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void limpiarBaseDeDatos() {
        limpiadorBaseDatos.limpiar();
    }

    @Nested
    class Registro {

        @Test
        void registroValidoDevuelve201ComoClienteSinExponerPassword() throws Exception {
            registrar("Ana", "Ana@Mail.com", "secreta123")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.email").value("ana@mail.com"))
                    .andExpect(jsonPath("$.rol").value("CLIENTE"))
                    .andExpect(jsonPath("$.fechaCreacion").exists())
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        void ignoraUnRolEnviadoEnElBody() throws Exception {
            String body = """
                    {"nombre": "Eva", "email": "eva@mail.com", "password": "secreta123", "rol": "ADMIN"}
                    """;
            mockMvc.perform(post(REGISTRO).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.rol").value("CLIENTE"));
        }

        @Test
        void emailDuplicadoDevuelve409() throws Exception {
            registrar("Ana", "ana@mail.com", "secreta123").andExpect(status().isCreated());

            registrar("Otra Ana", "ANA@mail.com", "otraClave123")
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Email ya registrado"));
        }

        @Test
        void datosInvalidosDevuelven400ConErroresPorCampo() throws Exception {
            registrar("", "no-es-un-email", "corta")
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errores.nombre").exists())
                    .andExpect(jsonPath("$.errores.email").exists())
                    .andExpect(jsonPath("$.errores.password").exists());
        }
    }

    @Nested
    class Login {

        @Test
        void credencialesCorrectasDevuelvenToken() throws Exception {
            registrar("Ana", "ana@mail.com", "secreta123");

            login("ana@mail.com", "secreta123")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(not(emptyString())))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(3600));
        }

        @Test
        void passwordIncorrectaDevuelve401() throws Exception {
            registrar("Ana", "ana@mail.com", "secreta123");

            login("ana@mail.com", "incorrecta")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
        }

        @Test
        void emailInexistenteDevuelveElMismo401() throws Exception {
            login("nadie@mail.com", "secreta123")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
        }
    }

    @Nested
    class RutasProtegidas {

        @Test
        void sinTokenDevuelve401() throws Exception {
            mockMvc.perform(get(ME))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void tokenInvalidoDevuelve401() throws Exception {
            mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer token.falso.invalido"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("El token es inválido o ha expirado"));
        }

        @Test
        void conTokenDevuelveElUsuarioAutenticado() throws Exception {
            registrar("Ana", "ana@mail.com", "secreta123");
            String token = obtenerToken("ana@mail.com", "secreta123");

            mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("ana@mail.com"))
                    .andExpect(jsonPath("$.rol").value("CLIENTE"));
        }

        @Test
        void clienteNoPuedeAccederARutaDeAdmin() throws Exception {
            registrar("Ana", "ana@mail.com", "secreta123");
            String token = obtenerToken("ana@mail.com", "secreta123");

            mockMvc.perform(get(SoloAdminTestController.RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void adminPuedeAccederARutaDeAdmin() throws Exception {
            usuarioRepository.save(new Usuario("Admin", "admin@mail.com", passwordEncoder.encode("claveAdmin1"), Rol.ADMIN));
            String token = obtenerToken("admin@mail.com", "claveAdmin1");

            mockMvc.perform(get(SoloAdminTestController.RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ok"));
        }
    }

    // --- helpers ---

    private ResultActions registrar(String nombre, String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "nombre", nombre, "email", email, "password", password));
        return mockMvc.perform(post(REGISTRO).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
        return mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String obtenerToken(String email, String password) throws Exception {
        String json = login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode nodo = objectMapper.readTree(json);
        return nodo.get("accessToken").asText();
    }
}
