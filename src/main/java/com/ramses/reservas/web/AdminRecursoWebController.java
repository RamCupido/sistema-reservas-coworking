package com.ramses.reservas.web;

import com.ramses.reservas.common.exception.HorarioInvalidoException;
import com.ramses.reservas.common.exception.NombreRecursoDuplicadoException;
import com.ramses.reservas.recurso.RecursoService;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.recurso.dto.RecursoResponse;
import com.ramses.reservas.reserva.ReservaService;
import com.ramses.reservas.web.form.RecursoForm;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** El acceso (solo ADMIN) lo controla la cadena web de SecurityConfig con "/admin/**". */
@Controller
@RequestMapping("/admin/recursos")
@RequiredArgsConstructor
public class AdminRecursoWebController {

    private static final String VISTA_FORM = "admin/recurso-form";

    private final RecursoService recursoService;
    private final ReservaService reservaService;
    private final ValidadorFormularios validador;

    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pagina", recursoService.listar(null, null, true,
                PageRequest.of(Math.max(page, 0), 20, Sort.by("nombre"))));
        model.addAttribute("urlPagina", "/admin/recursos");
        return "admin/recursos";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        return formulario(model, new RecursoForm(), null);
    }

    @PostMapping
    public String crear(@ModelAttribute("recurso") RecursoForm form, BindingResult resultado, Model model,
                        RedirectAttributes flash) {
        if (validarYGuardar(form, resultado, null)) {
            flash.addFlashAttribute("exito", "Recurso '" + form.getNombre().trim() + "' creado");
            return "redirect:/admin/recursos";
        }
        return formulario(model, form, null);
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        return formulario(model, RecursoForm.desde(recursoService.obtener(id, true)), id);
    }

    @PostMapping("/{id}")
    public String actualizar(@PathVariable Long id, @ModelAttribute("recurso") RecursoForm form,
                             BindingResult resultado, Model model, RedirectAttributes flash) {
        if (validarYGuardar(form, resultado, id)) {
            flash.addFlashAttribute("exito", "Recurso '" + form.getNombre().trim() + "' actualizado");
            return "redirect:/admin/recursos";
        }
        return formulario(model, form, id);
    }

    /**
     * Decisión de la Fase 4: desactivar NO cancela las reservas futuras (los clientes ya
     * cuentan con ellas). Se avisa al admin de cuántas hay para que decida caso a caso.
     */
    @PostMapping("/{id}/desactivar")
    public String desactivar(@PathVariable Long id, RedirectAttributes flash) {
        RecursoResponse recurso = recursoService.obtener(id, true);
        recursoService.desactivar(id);
        long futuras = reservaService.contarFuturas(id);

        flash.addFlashAttribute("exito", "Recurso '" + recurso.nombre() + "' desactivado");
        if (futuras > 0) {
            String texto = futuras == 1 ? " reserva futura que se mantiene" : " reservas futuras que se mantienen";
            flash.addFlashAttribute("aviso", "Tiene " + futuras + texto + ". Puedes revisarlas en Admin → Reservas.");
        }
        return "redirect:/admin/recursos";
    }

    /** true si se guardó; false si hay errores (ya registrados en el BindingResult). */
    private boolean validarYGuardar(RecursoForm form, BindingResult resultado, Long id) {
        validador.validar(form.toRequest(), resultado);
        if (resultado.hasErrors()) {
            return false;
        }
        try {
            if (id == null) {
                recursoService.crear(form.toRequest());
            } else {
                recursoService.actualizar(id, form.toRequest());
            }
            return true;
        } catch (NombreRecursoDuplicadoException e) {
            resultado.rejectValue("nombre", "duplicado", e.getMessage());
        } catch (HorarioInvalidoException e) {
            resultado.rejectValue("horaCierre", "horario", e.getMessage());
        }
        return false;
    }

    private static String formulario(Model model, RecursoForm form, Long id) {
        model.addAttribute("recurso", form);
        model.addAttribute("recursoId", id);
        model.addAttribute("tipos", TipoRecurso.values());
        model.addAttribute("accion", id == null ? "/admin/recursos" : "/admin/recursos/" + id);
        return VISTA_FORM;
    }
}
