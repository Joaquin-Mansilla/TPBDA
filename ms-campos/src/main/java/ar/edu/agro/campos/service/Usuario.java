package ar.edu.agro.campos.service;

import ar.edu.agro.commons.security.Roles;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Quién hace el pedido: el sub del token de Keycloak, sus roles y un nombre para mostrar.
 *
 * El sub es lo que identifica al usuario (nunca cambia) y es lo que se usa para la pertenencia (C6).
 * El nombre es solo para que se lea quién hizo cada cosa: se guarda una copia del momento en que se registró,
 * por ejemplo "operario1 (Oscar Operario)". Si el token no lo trae, es null.
 */
public record Usuario(String sub, Set<String> roles, String nombre) {

    public Usuario(String sub, Set<String> roles) {
        this(sub, roles, null);
    }

    public static Usuario de(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> c) {
            c.forEach(r -> roles.add(String.valueOf(r)));
        }
        return new Usuario(jwt.getSubject(), roles,
                nombreParaMostrar(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("name")));
    }

    /** "usuario (Nombre Apellido)", o lo que haya de los dos; null si no hay ninguno. */
    static String nombreParaMostrar(String usuario, String nombreCompleto) {
        boolean hayUsuario = usuario != null && !usuario.isBlank();
        boolean hayNombre = nombreCompleto != null && !nombreCompleto.isBlank();
        String texto;
        if (hayUsuario && hayNombre) {
            texto = usuario.trim() + " (" + nombreCompleto.trim() + ")";
        } else if (hayUsuario) {
            texto = usuario.trim();
        } else if (hayNombre) {
            texto = nombreCompleto.trim();
        } else {
            return null;
        }
        // las columnas son de 100 caracteres
        return texto.length() > 100 ? texto.substring(0, 100) : texto;
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
