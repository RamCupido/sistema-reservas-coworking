package com.ramses.reservas.web.form;

import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.recurso.dto.RecursoRequest;
import com.ramses.reservas.recurso.dto.RecursoResponse;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** Sin anotaciones de validación: se valida el {@link RecursoRequest} (única fuente de reglas). */
@Getter
@Setter
public class RecursoForm {

    private String nombre;
    private String descripcion;
    private TipoRecurso tipo;
    private Integer capacidad;

    // <input type="time"> envía "08:00"
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaApertura = LocalTime.of(8, 0);

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaCierre = LocalTime.of(20, 0);

    private boolean activo = true;

    public static RecursoForm desde(RecursoResponse recurso) {
        RecursoForm form = new RecursoForm();
        form.nombre = recurso.nombre();
        form.descripcion = recurso.descripcion();
        form.tipo = recurso.tipo();
        form.capacidad = recurso.capacidad();
        form.horaApertura = recurso.horaApertura();
        form.horaCierre = recurso.horaCierre();
        form.activo = recurso.activo();
        return form;
    }

    public RecursoRequest toRequest() {
        String descripcionLimpia = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        return new RecursoRequest(nombre, descripcionLimpia, tipo, capacidad,
                horaApertura, horaCierre, activo);
    }
}
