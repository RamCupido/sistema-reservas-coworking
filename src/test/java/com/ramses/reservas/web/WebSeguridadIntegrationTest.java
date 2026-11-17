package com.ramses.reservas.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.ramses.reservas.IntegracionTest;
import com.ramses.reservas.LimpiadorBaseDatos;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Cómo conviven las dos cadenas de seguridad: web (sesión + CSRF) y API (JWT, sin estado). */
@IntegracionTest
class WebSeguridadIntegrationTest {

    private static final String ANA = "ana@mail.com";
    private static final String PASSWORD = "secreta123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private LimpiadorBaseDatos limpiadorBaseDatos;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        limpiadorBaseDatos.limpiar();
        usuarioRepository.save(new Usuario("Ana", ANA, passwordEncoder.encode(PASSWORD), Rol.CLIENTE));
    }

    private MvcResult loginWeb() throws Exception {
        return mockMvc.perform(formLogin("/login").userParameter("email").user(ANA).password(PASSWORD))
                .andReturn();
    }

    // --- Cadena web ---

    @Test
    void elCatalogoEsPublico() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void loginCorrectoCreaLaSesionYVuelveAlInicio() throws Exception {
        mockMvc.perform(formLogin("/login").userParameter("email").user("ANA@Mail.com").password(PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(ANA).withRoles("CLIENTE"));
    }

    @Test
    void loginIncorrectoVuelveAlFormularioConError() throws Exception {
        mockMvc.perform(formLogin("/login").userParameter("email").user(ANA).password("incorrecta"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
    }

    @Test
    void unaPaginaProtegidaRedirigeAlLogin() throws Exception {
        mockMvc.perform(get("/mis-reservas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void unPostSinTokenCsrfEsRechazado() throws Exception {
        mockMvc.perform(post("/reservas").with(user(ANA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unClienteNoEntraAlPanelDeAdmin() throws Exception {
        mockMvc.perform(get("/admin/recursos").with(user(ANA).roles("CLIENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unAdminEntraAlPanel() throws Exception {
        mockMvc.perform(get("/admin/recursos").with(user("admin@mail.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/recursos"));
    }

    @Test
    void elLogoutEsPorPostYCierraLaSesion() throws Exception {
        mockMvc.perform(post("/logout").with(user(ANA)).with(csrf()))
                .andExpect(redirectedUrl("/?logout"))
                .andExpect(unauthenticated());
    }

    @Test
    void unRecursoInexistenteMuestraLaPaginaDeError404() throws Exception {
        mockMvc.perform(get("/recursos/999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void unaRutaInexistenteFueraDeLaApiNoRespondeJson() throws Exception {
        // Un anónimo sería redirigido al login (anyRequest().authenticated()): no se revela qué rutas existen.
        // MockMvc no hace el "forward" a /error, así que solo se comprueba el código y que no es ProblemDetail.
        MvcResult resultado = mockMvc.perform(get("/pagina-inventada").with(user(ANA)).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andReturn();

        assertThat(resultado.getResponse().getContentType()).isNull();
    }

    // --- Cadena API: sigue igual que antes ---

    @Test
    void laApiNoCreaSesion() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ANA + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(resultado.getRequest().getSession(false)).isNull();
    }

    /**
     * Por esto la API puede tener CSRF desactivado: la cookie de sesión de las páginas NO sirve
     * para autenticarse en la API, así que una web maliciosa no puede usarla para llamar a /api.
     */
    @Test
    void laCookieDeSesionDeLaWebNoAutenticaEnLaApi() throws Exception {
        MockHttpSession sesion = (MockHttpSession) loginWeb().getRequest().getSession(false);
        assertThat(sesion).isNotNull();

        mockMvc.perform(get("/mis-reservas").session(sesion))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/usuarios/me").session(sesion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unaRutaInexistenteDeLaApiSigueRespondiendoProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
