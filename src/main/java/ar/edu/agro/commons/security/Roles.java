package ar.edu.agro.commons.security;

import java.util.Set;

/** Nombres de los roles definidos en Keycloak. Usarlos con hasRole(...) (sin el prefijo ROLE_). */
public final class Roles {

    public static final String PRODUCTOR = "PRODUCTOR";
    public static final String OPERARIO = "OPERARIO";
    public static final String AGRONOMO = "AGRONOMO";

    public static final String AUTHORITY_PREFIX = "ROLE_";
    public static final Set<String> TODOS = Set.of(PRODUCTOR, OPERARIO, AGRONOMO);

    private Roles() {
    }
}
