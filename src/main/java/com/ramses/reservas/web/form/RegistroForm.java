package com.ramses.reservas.web.form;

import com.ramses.reservas.auth.dto.RegistroRequest;
import lombok.Getter;
import lombok.Setter;

/** Sin anotaciones de validación: se valida el {@link RegistroRequest} (única fuente de reglas). */
@Getter
@Setter
public class RegistroForm {

    private String nombre;
    private String email;
    private String password;
    private String confirmarPassword;

    public RegistroRequest toRequest() {
        return new RegistroRequest(nombre, email, password);
    }

    /** Las contraseñas nunca se devuelven al navegador al re-mostrar el formulario. */
    public void limpiarPasswords() {
        password = null;
        confirmarPassword = null;
    }
}
