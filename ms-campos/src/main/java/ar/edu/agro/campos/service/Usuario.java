package ar.edu.agro.campos.service;

import ar.edu.agro.commons.security.Roles;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Quién hace el pedido: el sub del token de Keycloak y sus roles. */
public record Usuario(String sub, Set<String> roles) {

    public static Usuario de(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> c) {
            c.forEach(r -> roles.add(String.valueOf(r)));
        }
        return new Usuario(jwt.getSubject(), roles);
    }

    public boolean esProductor() {
        return roles.contains(Roles.PRODUCTOR);
    }

    public boolean esOperario() {
        return roles.contains(Roles.OPERARIO);
    }

    public boolean esAgronomo() {
        return roles.contains(Roles.AGRONOMO);
    }
}
