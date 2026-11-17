package com.ramses.reservas.web;

import org.springframework.web.util.UriComponentsBuilder;

/** Construye URLs internas con parámetros tipados (sin concatenar texto del usuario). */
final class Urls {

    private Urls() {
    }

    /**
     * @param pares nombre, valor, nombre, valor... Los valores null se omiten.
     */
    static String conParametros(String ruta, Object... pares) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(ruta);
        for (int i = 0; i < pares.length; i += 2) {
            if (pares[i + 1] != null) {
                builder.queryParam((String) pares[i], pares[i + 1]);
            }
        }
        return builder.encode().build().toUriString();
    }

    static String redirect(String ruta, Object... pares) {
        return "redirect:" + conParametros(ruta, pares);
    }
}
