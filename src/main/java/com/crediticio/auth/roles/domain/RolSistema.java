package com.crediticio.auth.roles.domain;

import java.util.Set;

/**
 * Roles del sistema definidos por HU08 (RF06). Son los unicos que pueden asignarse a un usuario.
 */
public final class RolSistema {

    public static final String ADMINISTRADOR_RIESGO = "ADMINISTRADOR_RIESGO";
    public static final String ANALISTA_CREDITO = "ANALISTA_CREDITO";

    public static final Set<String> PERMITIDOS = Set.of(ADMINISTRADOR_RIESGO, ANALISTA_CREDITO);

    private RolSistema() {
    }
}
