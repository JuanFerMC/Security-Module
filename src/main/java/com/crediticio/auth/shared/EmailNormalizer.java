package com.crediticio.auth.shared;

import java.util.Locale;

/**
 * Normaliza correos (HU08 RF02): sin espacios laterales y en minusculas, para evitar
 * duplicados por diferencias de mayusculas/minusculas.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
