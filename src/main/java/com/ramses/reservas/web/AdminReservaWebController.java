package com.ramses.reservas.web;

import com.ramses.reservas.common.dto.PaginaResponse;
import com.ramses.reservas.common.exception.ConflictoException;
import com.ramses.reservas.common.exception.FiltroInvalidoException;
import com.ramses.reservas.common.exception.ReglaNegocioException;
import com.ramses.reservas.reserva.EstadoReserva;
import com.ramses.reservas.reserva.ReservaService;
import com.ramses.reservas.reserva.dto.ReservaResponse;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/reservas")
@RequiredArgsConstructor
public class AdminReservaWebController {

    private static final String RUTA = "/admin/reservas";

    private final ReservaService reservaService;

    @GetMapping
    public String listar(@RequestParam(required = false) EstadoReserva estado,
                         @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                         @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        model.addAttribute("estados", EstadoReserva.values());
        model.addAttribute("estado", estado);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("page", page);
        try {
            PaginaResponse<ReservaResponse> pagina = reservaService.listarTodas(estado, desde, hasta,
                    PageRequest.of(Math.max(page, 0), 20, Sort.by(Sort.Direction.DESC, "fechaInicio")));
            Set<Long> cancelables = pagina.content().stream()
                    .filter(reserva -> reservaService.esCancelable(reserva, true))
                    .map(ReservaResponse::id)
                    .collect(Collectors.toSet());
            model.addAttribute("pagina", pagina);
            model.addAttribute("cancelables", cancelables);
            model.addAttribute("urlPagina", Urls.conParametros(RUTA, "estado", estado, "desde", desde, "hasta", hasta));
        } catch (FiltroInvalidoException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "admin/reservas";
    }

    /** Los filtros llegan como campos ocultos tipados, para volver al mismo listado tras cancelar. */
    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable Long id,
                           @RequestParam(required = false) EstadoReserva estado,
                           @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate hasta,
                           @RequestParam(defaultValue = "0") int page,
                           Authentication authentication, RedirectAttributes flash) {
        try {
            reservaService.cancelar(id, authentication.getName(), true);
            flash.addFlashAttribute("exito", "Reserva #" + id + " cancelada");
        } catch (ReglaNegocioException | ConflictoException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return Urls.redirect(RUTA, "estado", estado, "desde", desde, "hasta", hasta, "page", page);
    }
}
