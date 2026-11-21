package com.ramses.reservas.web;

import static com.ramses.reservas.common.util.SeguridadUtils.esAdmin;

import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.common.exception.ConflictoException;
import com.ramses.reservas.common.exception.ReglaNegocioException;
import com.ramses.reservas.reserva.EstadoReserva;
import com.ramses.reservas.reserva.ReservaService;
import com.ramses.reservas.reserva.ReservasProperties;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import com.ramses.reservas.web.form.ReservaForm;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
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
    static final String VISTA_CALENDARIO = "calendario";
    static final String VISTA_LISTA = "lista";
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    private final ReservaService reservaService;
    private final ValidadorFormularios validador;
    private final ReservasProperties reservasProperties;
    private final Clock clock;

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
            // Se aterriza en el calendario del mes de la reserva, con ella resaltada.
            flash.addFlashAttribute("reservaNueva", creada.id());
            return Urls.redirect("/mis-reservas", "mes", YearMonth.from(creada.fechaInicio()));
        } catch (ReglaNegocioException | ConflictoException e) {
            // Horario ocupado, fuera de horario, en el pasado...: se avisa en la misma agenda.
            flash.addFlashAttribute("error", e.getMessage());
            return volver;
        }
    }

    /**
     * Dos vistas de la misma página:
     * <ul>
     *   <li>calendario (por defecto): un mes, con la agenda de ese mes debajo;</li>
     *   <li>lista: historial paginado con filtro por estado.</li>
     * </ul>
     */
    @GetMapping("/mis-reservas")
    public String misReservas(@RequestParam(defaultValue = VISTA_CALENDARIO) String vista,
                              @RequestParam(required = false) YearMonth mes,
                              @RequestParam(required = false) EstadoReserva estado,
                              @RequestParam(defaultValue = "0") int page,
                              Authentication authentication, Model model) {
        boolean admin = esAdmin(authentication);
        LocalDate hoy = LocalDate.now(clock.withZone(reservasProperties.zonaHoraria()));
        model.addAttribute("hoy", hoy);
        reservaService.proxima(authentication.getName()).ifPresent(r -> model.addAttribute("proxima", r));

        if (VISTA_LISTA.equals(vista)) {
            PaginaResponse<ReservaResponse> pagina = reservaService.listarMias(authentication.getName(), estado,
                    PageRequest.of(Math.max(page, 0), TAMANO_PAGINA, Sort.by(Sort.Direction.DESC, "fechaInicio")));
            model.addAttribute("vista", VISTA_LISTA);
            model.addAttribute("pagina", pagina);
            model.addAttribute("cancelables", cancelables(pagina.content(), admin));
            model.addAttribute("estados", EstadoReserva.values());
            model.addAttribute("estadoSeleccionado", estado);
            model.addAttribute("urlPagina", Urls.conParametros("/mis-reservas", "vista", VISTA_LISTA, "estado", estado));
            return "mis-reservas";
        }

        YearMonth mesVisible = mes != null ? mes : YearMonth.from(hoy);
        List<ReservaResponse> reservas = reservaService.listarMiasEntre(authentication.getName(),
                CalendarioMensual.inicioCuadricula(mesVisible), CalendarioMensual.finCuadricula(mesVisible));
        model.addAttribute("vista", VISTA_CALENDARIO);
        model.addAttribute("calendario", CalendarioMensual.construir(mesVisible, hoy, reservas));
        model.addAttribute("cancelables", cancelables(reservas, admin));
        model.addAttribute("mesAnterior", mesVisible.minusMonths(1));
        model.addAttribute("mesSiguiente", mesVisible.plusMonths(1));
        model.addAttribute("esMesActual", mesVisible.equals(YearMonth.from(hoy)));
        return "mis-reservas";
    }

    /** Vuelve a la misma vista (y mes o página) desde la que se canceló. Parámetros tipados: sin open redirect. */
    @PostMapping("/mis-reservas/{id}/cancelar")
    public String cancelar(@PathVariable Long id,
                           @RequestParam(defaultValue = VISTA_CALENDARIO) String vista,
                           @RequestParam(required = false) YearMonth mes,
                           @RequestParam(required = false) EstadoReserva estado,
                           @RequestParam(required = false) Integer page,
                           Authentication authentication, RedirectAttributes flash) {
        try {
            reservaService.cancelar(id, authentication.getName(), esAdmin(authentication));
            flash.addFlashAttribute("exito", "Reserva cancelada");
        } catch (ReglaNegocioException | ConflictoException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return VISTA_LISTA.equals(vista)
                ? Urls.redirect("/mis-reservas", "vista", VISTA_LISTA, "estado", estado, "page", page)
                : Urls.redirect("/mis-reservas", "mes", mes);
    }

    /** Ids de las reservas que muestran el botón "Cancelar" (misma regla que el service). */
    private Set<Long> cancelables(Collection<ReservaResponse> reservas, boolean esAdmin) {
        return reservas.stream()
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
