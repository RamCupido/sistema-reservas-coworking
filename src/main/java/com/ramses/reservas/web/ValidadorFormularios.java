package com.ramses.reservas.web;

import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

/**
 * Los formularios web son clases mutables (Thymeleaf y th:field trabajan con getters/setters),
 * pero las reglas de validación viven una sola vez: en los records de la API (RecursoRequest,
 * RegistroRequest...). Este componente valida el record y vuelca los errores en el
 * BindingResult del formulario; funciona porque los campos se llaman igual en ambos.
 */
@Component
public class ValidadorFormularios {

    private final SpringValidatorAdapter adaptador;

    public ValidadorFormularios(Validator validator) {
        this.adaptador = new SpringValidatorAdapter(validator);
    }

    public void validar(Object request, BindingResult resultado) {
        adaptador.validate(request, resultado);
    }
}
