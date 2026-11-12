package com.ramses.reservas.common.util;

import java.util.Locale;

public final class EmailUtils {

    private EmailUtils() {
    }

    /** Ana@Mail.com y " ana@mail.com " se consideran el mismo email. */
    public static String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
