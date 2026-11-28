package com.ramses.reservas.web;

import static com.ramses.reservas.common.util.SeguridadUtils.esAdmin;

import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.common.exception.ConflictoException;
import com.ramses.reservas.common.exception.ReglaNegocioException;
import com.ramses.reservas.common.exception.UsuarioNoEncontradoException;
import com.ramses.reservas.recurso.RecursoService;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.recurso.dto.RecursoResponse;
import com.ramses.reservas.reserva.DisponibilidadService;
import com.ramses.reservas.reserva.ReservaService;
import com.ramses.reservas.reserva.ReservasProperties;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import com.ramses.reservas.usuario.UsuarioService;
import com.ramses.reservas.web.form.ReservaForm;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Páginas públicas. Igual que los @RestController, son delgados: llaman a los mismos
 * services de la API (sin pasar por HTTP), así las reglas de negocio viven en un solo sitio.
 */
@Controller
@RequiredArgsConstructor
public class CatalogoWebController {

    private static final int TAMANO_PAGINA = 12;
    private static final int MINUTOS_BLOQUE = 30;
    private static final String MIS_RESERVAS = "redirect:/mis-reservas";

    private final RecursoService recursoService;
    private final DisponibilidadService disponibilidadService;
    private final ReservaService reservaService;
    private final UsuarioService usuarioService;
    private final ReservasProperties reservasProperties;
    private final Clock clock;

    /**
     * Visitante: página de presentación (qué es, cómo funciona) + catálogo.
     * Usuario con sesión: saludo, su próxima reserva + catálogo.
     * authentication es null para visitantes anónimos.
     */
    @GetMapping("/")
    public String catalogo(@RequestParam(required = false) TipoRecurso tipo,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(required = false) Long modificar,
                           Authentication authentication, Model model, RedirectAttributes flash) {
        ReservaResponse modificando;
        try {
            modificando = reservaEnModificacion(modificar, authentication);
        } catch (ReglaNegocioException | ConflictoException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return MIS_RESERVAS;
        }
        if (authentication != null) {
            try {
                model.addAttribute("nombreUsuario", usuarioService.obtenerPorEmail(authentication.getName()).nombre());
            } catch (UsuarioNoEncontradoException e) {
                // Sesión de un usuario ya borrado: la portada se muestra igual, sin saludo personalizado.
            }
            reservaService.proxima(authentication.getName()).ifPresent(r -> model.addAttribute("proxima", r));
        }
        PaginaResponse<RecursoResponse> pagina = recursoService.listar(tipo, null, false,
                PageRequest.of(Math.max(page, 0), TAMANO_PAGINA, Sort.by("nombre")));
        model.addAttribute("pagina", pagina);
        model.addAttribute("tipos", TipoRecurso.values());
        model.addAttribute("tipoSeleccionado", tipo);
        model.addAttribute("modificando", modificando);
        model.addAttribute("urlPagina", Urls.conParametros("/", "tipo", tipo,
                "modificar", modificando == null ? null : modificando.id()));
        return "index";
    }

    /**
     * Agenda de un día. Una fecha pasada (o ausente) se lleva a hoy: no tiene sentido mostrarla.
     * Con {@code modificar} (y sesión iniciada) se abre en "modo modificar" esa reserva: sus bloques
     * cuentan como libres y el formulario guarda el cambio en vez de crear otra reserva.
     */
    @GetMapping("/recursos/{id}")
    public String recurso(@PathVariable Long id,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                          LocalDate fecha,
                          @RequestParam(required = false) Long modificar,
                          Authentication authentication, Model model, RedirectAttributes flash) {
        LocalDate hoy = LocalDate.now(clock.withZone(reservasProperties.zonaHoraria()));
        LocalDate dia = fecha == null || fecha.isBefore(hoy) ? hoy : fecha;
        ReservaResponse modificando;
        try {
            modificando = reservaEnModificacion(modificar, authentication);
        } catch (ReglaNegocioException | ConflictoException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return MIS_RESERVAS;
        }

        ReservaForm form = new ReservaForm();
        form.setRecursoId(id);
        form.setFecha(dia);

        model.addAttribute("recurso", recursoService.obtener(id, false));
        model.addAttribute("disponibilidad",
                disponibilidadService.calcular(id, dia, modificando == null ? null : modificando.id()));
        model.addAttribute("hoy", hoy);
        model.addAttribute("reserva", form);
        model.addAttribute("modificando", modificando);
        model.addAttribute("bloquesActuales", bloquesActuales(modificando, id, dia));
        return "recurso";
    }

    /**
     * null si no se está modificando (o el visitante es anónimo: el parámetro se ignora).
     * Una reserva ajena lanza 404; una que no se puede modificar, la excepción con el motivo.
     */
    private ReservaResponse reservaEnModificacion(Long modificar, Authentication authentication) {
        if (modificar == null || authentication == null) {
            return null;
        }
        return reservaService.obtenerModificable(modificar, authentication.getName(), esAdmin(authentication));
    }

    /** Inicios de los bloques que ocupa la reserva que se modifica, si está en este espacio y día. */
    private static Set<LocalTime> bloquesActuales(ReservaResponse modificando, Long recursoId, LocalDate dia) {
        Set<LocalTime> bloques = new HashSet<>();
        if (modificando == null || !modificando.recurso().id().equals(recursoId)
                || !modificando.fechaInicio().toLocalDate().equals(dia)) {
            return bloques;
        }
        for (LocalTime hora = modificando.fechaInicio().toLocalTime();
             hora.isBefore(modificando.fechaFin().toLocalTime());
             hora = hora.plusMinutes(MINUTOS_BLOQUE)) {
            bloques.add(hora);
        }
        return bloques;
    }
}
