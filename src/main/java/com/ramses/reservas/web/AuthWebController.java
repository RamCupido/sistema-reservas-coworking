package com.ramses.reservas.web;

import com.ramses.reservas.auth.AuthService;
import com.ramses.reservas.common.exception.EmailYaRegistradoException;
import com.ramses.reservas.web.form.RegistroForm;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * El POST /login no está aquí: lo procesa el filtro formLogin de Spring Security
 * (comprueba la contraseña con BCrypt y crea la sesión). Este controller solo pinta el formulario.
 */
@Controller
@RequiredArgsConstructor
public class AuthWebController {

    private final AuthService authService;
    private final ValidadorFormularios validador;

    /** authentication es null para visitantes anónimos. */
    @GetMapping("/login")
    public String login(Authentication authentication) {
        return authentication != null ? "redirect:/" : "login";
    }

    @GetMapping("/registro")
    public String registro(Authentication authentication, Model model) {
        if (authentication != null) {
            return "redirect:/";
        }
        model.addAttribute("registro", new RegistroForm());
        return "registro";
    }

    @PostMapping("/registro")
    public String registrar(@ModelAttribute("registro") RegistroForm form, BindingResult resultado) {
        validador.validar(form.toRequest(), resultado);
        if (!Objects.equals(form.getPassword(), form.getConfirmarPassword())) {
            resultado.rejectValue("confirmarPassword", "noCoincide", "Las contraseñas no coinciden");
        }

        if (!resultado.hasErrors()) {
            try {
                authService.registrar(form.toRequest());
                return "redirect:/login?registrado";
            } catch (EmailYaRegistradoException e) {
                resultado.rejectValue("email", "duplicado", "Ya existe una cuenta con ese email");
            }
        }
        form.limpiarPasswords();
        return "registro";
    }
}
