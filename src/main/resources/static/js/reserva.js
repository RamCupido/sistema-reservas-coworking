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

    actualizar();
})();
