package ar.edu.agro.gateway.config;

import java.util.Set;

/**
 * Roles del realm agro de Keycloak. Es una copia mínima de ar.edu.agro.commons.security.Roles:
 * el gateway es reactivo y no usa agro-commons. Si cambian los roles en Keycloak, actualizar acá también.
 */
final class Roles {

    static final String PRODUCTOR = "PRODUCTOR";
    static final String OPERARIO = "OPERARIO";
    static final String AGRONOMO = "AGRONOMO";

    static final Set<String> TODOS = Set.of(PRODUCTOR, OPERARIO, AGRONOMO);

    private Roles() {
    }
}
