package com.ramses.reservas.web;

import static com.ramses.reservas.common.util.SeguridadUtils.esAdmin;

import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.common.exception.ConflictoException;
import com.ramses.reservas.common.exception.ReglaNegocioException;
import com.ramses.reservas.reserva.EstadoReserva;
import com.ramses.reservas.reserva.ReservaService;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import com.ramses.reservas.web.form.ReservaForm;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Patrón PRG (Post/Redirect/Get): tras cada POST se redirige, así recargar la página no
 * repite la reserva. Los mensajes viajan como "flash attributes": se guardan en la sesión
 * solo hasta la siguiente petición.
 */
@Controller
@RequiredArgsConstructor
public class ReservaWebController {

    private static final int TAMANO_PAGINA = 10;
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    private final ReservaService reservaService;
    private final ValidadorFormularios validador;

    @PostMapping("/reservas")
    public String reservar(@ModelAttribute ReservaForm form, BindingResult resultado,
                           Authentication authentication, RedirectAttributes flash) {
        String volver = form.getRecursoId() == null ? "redirect:/"
                : Urls.redirect("/recursos/" + form.getRecursoId(), "fecha", form.getFecha());

        validador.validar(form.toRequest(), resultado);
        if (resultado.hasErrors()) {
            flash.addFlashAttribute("error", primerError(resultado));
            return volver;
        }
        try {
            ReservaResponse creada = reservaService.crear(form.toRequest(), authentication.getName());
            flash.addFlashAttribute("exito", "Reserva confirmada: " + creada.recurso().nombre() + ", "
                    + creada.fechaInicio().format(FORMATO));
            return "redirect:/mis-reservas";
        } catch (ReglaNegocioException | ConflictoException e) {
            // Horario ocupado, fuera de horario, en el pasado...: se avisa en la misma agenda.
            flash.addFlashAttribute("error", e.getMessage());
            return volver;
        }
    }

    @GetMapping("/mis-reservas")
    public String misReservas(@RequestParam(required = false) EstadoReserva estado,
                              @RequestParam(defaultValue = "0") int page,
                              Authentication authentication, Model model) {
        PaginaResponse<ReservaResponse> pagina = reservaService.listarMias(authentication.getName(), estado,
                PageRequest.of(Math.max(page, 0), TAMANO_PAGINA, Sort.by(Sort.Direction.DESC, "fechaInicio")));

        model.addAttribute("pagina", pagina);
        model.addAttribute("cancelables", cancelables(pagina, esAdmin(authentication)));
        model.addAttribute("estados", EstadoReserva.values());
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("urlPagina", Urls.conParametros("/mis-reservas", "estado", estado));
        return "mis-reservas";
    }

    @PostMapping("/mis-reservas/{id}/cancelar")
    public String cancelar(@PathVariable Long id, Authentication authentication, RedirectAttributes flash) {
        try {
            reservaService.cancelar(id, authentication.getName(), esAdmin(authentication));
            flash.addFlashAttribute("exito", "Reserva cancelada");
        } catch (ReglaNegocioException | ConflictoException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/mis-reservas";
    }

    /** Ids de las reservas que muestran el botón "Cancelar" (misma regla que el service). */
    private Set<Long> cancelables(PaginaResponse<ReservaResponse> pagina, boolean esAdmin) {
        return pagina.content().stream()
                .filter(reserva -> reservaService.esCancelable(reserva, esAdmin))
                .map(ReservaResponse::id)
                .collect(Collectors.toSet());
    }

    /** Un error de conversión ("abc" como hora) tiene un mensaje técnico: se muestra uno genérico. */
    private static String primerError(BindingResult resultado) {
        FieldError error = resultado.getFieldError();
        if (error == null || error.isBindingFailure()) {
            return "Elige un horario válido";
        }
        return error.getDefaultMessage();
    }
}
