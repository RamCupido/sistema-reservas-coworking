package com.ramses.reservas.web;

import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.recurso.RecursoService;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.recurso.dto.RecursoResponse;
import com.ramses.reservas.reserva.DisponibilidadService;
import com.ramses.reservas.reserva.ReservasProperties;
import com.ramses.reservas.web.form.ReservaForm;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Páginas públicas. Igual que los @RestController, son delgados: llaman a los mismos
 * services de la API (sin pasar por HTTP), así las reglas de negocio viven en un solo sitio.
 */
@Controller
@RequiredArgsConstructor
public class CatalogoWebController {

    private static final int TAMANO_PAGINA = 12;

    private final RecursoService recursoService;
    private final DisponibilidadService disponibilidadService;
    private final ReservasProperties reservasProperties;
    private final Clock clock;

    @GetMapping("/")
    public String catalogo(@RequestParam(required = false) TipoRecurso tipo,
                           @RequestParam(defaultValue = "0") int page, Model model) {
        PaginaResponse<RecursoResponse> pagina = recursoService.listar(tipo, null, false,
                PageRequest.of(Math.max(page, 0), TAMANO_PAGINA, Sort.by("nombre")));
        model.addAttribute("pagina", pagina);
        model.addAttribute("tipos", TipoRecurso.values());
        model.addAttribute("tipoSeleccionado", tipo);
        model.addAttribute("urlPagina", Urls.conParametros("/", "tipo", tipo));
        return "index";
    }

    /** Agenda de un día. Una fecha pasada (o ausente) se lleva a hoy: no tiene sentido mostrarla. */
    @GetMapping("/recursos/{id}")
    public String recurso(@PathVariable Long id,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                          LocalDate fecha,
                          Model model) {
        LocalDate hoy = LocalDate.now(clock.withZone(reservasProperties.zonaHoraria()));
        LocalDate dia = fecha == null || fecha.isBefore(hoy) ? hoy : fecha;

        ReservaForm form = new ReservaForm();
        form.setRecursoId(id);
        form.setFecha(dia);

        model.addAttribute("recurso", recursoService.obtener(id, false));
        model.addAttribute("disponibilidad", disponibilidadService.calcular(id, dia));
        model.addAttribute("hoy", hoy);
        model.addAttribute("reserva", form);
        return "recurso";
    }
}
