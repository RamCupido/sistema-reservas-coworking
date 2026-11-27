/*
 * Selección de horario en la agenda. Es solo una ayuda de usabilidad: el servidor vuelve a
 * validar todo (horario, bloques de 30 min, solapamiento), así que no hay reglas "de verdad" aquí.
 */
(function () {
    'use strict';

    const form = document.getElementById('form-reserva');
    if (!form) {
        return; // visitante anónimo: no hay formulario
    }

    const bloques = Array.from(document.querySelectorAll('#agenda .bloque'));
    const selInicio = document.getElementById('horaInicio');
    const selFin = document.getElementById('horaFin');
    const resumen = document.getElementById('resumen-seleccion');
    const repetir = document.getElementById('repetir');
    const opcionesRepeticion = document.getElementById('opciones-repeticion');
    const selSemanas = document.getElementById('semanas');
    const boton = document.getElementById('boton-reservar');

    const minutos = (hhmm) => {
        const [h, m] = hhmm.split(':').map(Number);
        return h * 60 + m;
    };

    /** Finales posibles para un inicio: se avanza mientras los bloques sigan libres y contiguos. */
    function finesPosibles(inicio) {
        const fines = new Set();
        const desde = bloques.findIndex((b) => b.dataset.inicio === inicio);
        for (let i = desde; i >= 0 && i < bloques.length && bloques[i].dataset.libre === 'true'; i++) {
            fines.add(bloques[i].dataset.fin);
        }
        return fines;
    }

    function actualizar() {
        const inicio = selInicio.value;
        const fines = inicio ? finesPosibles(inicio) : new Set();

        for (const opcion of selFin.options) {
            if (opcion.value) {
                opcion.disabled = !fines.has(opcion.value);
            }
        }
        if (selFin.value && !fines.has(selFin.value)) {
            selFin.value = '';
        }

        const fin = selFin.value;
        bloques.forEach((b) => {
            const dentro = inicio && fin
                && minutos(b.dataset.inicio) >= minutos(inicio) && minutos(b.dataset.fin) <= minutos(fin);
            b.classList.toggle('seleccionado', Boolean(dentro));
        });

        const semanal = repetir.checked;
        const semanas = Number(selSemanas.value);
        opcionesRepeticion.hidden = !semanal;
        resumen.textContent = inicio && fin
            ? `${inicio} – ${fin} · ${duracion(minutos(fin) - minutos(inicio))}${semanal ? ` · ${semanas} semanas` : ''}`
            : 'Elige un horario en la agenda';
        boton.textContent = semanal ? `Confirmar ${semanas} reservas` : 'Confirmar reserva';
    }

    /** 90 → "1 h 30 min" */
    function duracion(total) {
        const h = Math.floor(total / 60);
        const m = total % 60;
        return [h ? `${h} h` : '', m ? `${m} min` : ''].filter(Boolean).join(' ');
    }

    // Clic en la agenda: el primero marca el inicio; el segundo (si es posterior y contiguo) el final.
    bloques.forEach((b) => b.addEventListener('click', () => {
        const inicio = selInicio.value;
        const yaHayRango = inicio && selFin.value;
        if (!inicio || yaHayRango || !finesPosibles(inicio).has(b.dataset.fin)) {
            selInicio.value = b.dataset.inicio;
            selFin.value = b.dataset.fin;
        } else {
            selFin.value = b.dataset.fin;
        }
        actualizar();
    }));

    selInicio.addEventListener('change', () => {
        selFin.value = selInicio.value ? finesPosibles(selInicio.value).values().next().value : '';
        actualizar();
    });
    selFin.addEventListener('change', actualizar);
    repetir.addEventListener('change', actualizar);
    selSemanas.addEventListener('change', actualizar);

    actualizar();
})();
