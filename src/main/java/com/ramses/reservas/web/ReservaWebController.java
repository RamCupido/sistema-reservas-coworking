package com.ramses.reservas.web;

import static com.ramses.reservas.common.util.SeguridadUtils.esAdmin;

import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.common.exception.ConflictoException;
import com.ramses.reservas.common.exception.ReglaNegocioException;
import com.ramses.reservas.reserva.EstadoReserva;
import com.ramses.reservas.reserva.ReservaService;
import com.ramses.reservas.reserva.ReservasProperties;
import com.ramses.reservas.reserva.SerieReservaService;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import com.ramses.reservas.reserva.dto.SerieResponse;
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
    private final SerieReservaService serieService;
    private final ValidadorFormularios validador;
    private final ReservasProperties reservasProperties;
    private final Clock clock;

    @PostMapping("/reservas")
    public String reservar(@ModelAttribute ReservaForm form, BindingResult resultado,
                           Authentication authentication, RedirectAttributes flash) {
        String volver = form.getRecursoId() == null ? "redirect:/"
                : Urls.redirect("/recursos/" + form.getRecursoId(), "fecha", form.getFecha());

        validador.validar(form.isRepetir() ? form.toSerieRequest() : form.toRequest(), resultado);
        if (resultado.hasErrors()) {
            flash.addFlashAttribute("error", primerError(resultado));
            return volver;
        }
        try {
            if (form.isRepetir()) {
                SerieResponse serie = serieService.crear(form.toSerieRequest(), authentication.getName());
                ReservaResponse primera = serie.reservas().getFirst();
                flash.addFlashAttribute("exito", "Reserva semanal confirmada: " + serie.recurso().nombre()
                        + ", " + serie.reservas().size() + " semanas desde el " + primera.fechaInicio().format(FORMATO));
                flash.addFlashAttribute("serieNueva", serie.id());
                return Urls.redirect("/mis-reservas", "mes", YearMonth.from(primera.fechaInicio()));
            }
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
     * Guarda el cambio hecho en la agenda en "modo modificar". Si falla (hueco ocupado, fuera de plazo...),
     * vuelve a la misma agenda y día, sin salir del modo modificar.
     */
    @PostMapping("/mis-reservas/{id}/modificar")
    public String modificar(@PathVariable Long id, @ModelAttribute ReservaForm form, BindingResult resultado,
                            Authentication authentication, RedirectAttributes flash) {
        String volver = form.getRecursoId() == null ? "redirect:/mis-reservas"
                : Urls.redirect("/recursos/" + form.getRecursoId(), "fecha", form.getFecha(), "modificar", id);

        validador.validar(form.toModificarRequest(), resultado);
        if (resultado.hasErrors()) {
            flash.addFlashAttribute("error", primerError(resultado));
            return volver;
        }
        try {
            ReservaResponse modificada = reservaService.modificar(id, form.toModificarRequest(),
                    authentication.getName(), esAdmin(authentication));
            flash.addFlashAttribute("exito", "Reserva modificada: " + modificada.recurso().nombre() + ", "
                    + modificada.fechaInicio().format(FORMATO));
            flash.addFlashAttribute("reservaModificada", modificada.id());
            return Urls.redirect("/mis-reservas", "mes", YearMonth.from(modificada.fechaInicio()));
        } catch (ReglaNegocioException | ConflictoException e) {
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
            model.addAttribute("modificables", modificables(pagina.content(), admin));
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
        model.addAttribute("modificables", modificables(reservas, admin));
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
        return volverAMisReservas(vista, mes, estado, page);
    }

    /** "Cancelar las restantes": todas las fechas de la serie que aún se pueden cancelar. */
    @PostMapping("/mis-reservas/series/{id}/cancelar")
    public String cancelarSerie(@PathVariable Long id,
                                @RequestParam(defaultValue = VISTA_CALENDARIO) String vista,
                                @RequestParam(required = false) YearMonth mes,
                                @RequestParam(required = false) EstadoReserva estado,
                                @RequestParam(required = false) Integer page,
                                Authentication authentication, RedirectAttributes flash) {
        try {
            SerieResponse serie = serieService.cancelarRestantes(id, authentication.getName(), esAdmin(authentication));
            flash.addFlashAttribute("exito", "Reserva semanal cancelada: se anularon las fechas restantes de "
                    + serie.recurso().nombre());
        } catch (ReglaNegocioException | ConflictoException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volverAMisReservas(vista, mes, estado, page);
    }

    private static String volverAMisReservas(String vista, YearMonth mes, EstadoReserva estado, Integer page) {
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

    /** Ids de las reservas que muestran el botón "Modificar" (misma regla que el service). */
    private Set<Long> modificables(Collection<ReservaResponse> reservas, boolean esAdmin) {
        return reservas.stream()
                .filter(reserva -> reservaService.esModificable(reserva, esAdmin))
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
