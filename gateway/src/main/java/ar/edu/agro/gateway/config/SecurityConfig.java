package ar.edu.agro.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import static ar.edu.agro.gateway.config.Roles.AGRONOMO;
import static ar.edu.agro.gateway.config.Roles.OPERARIO;
import static ar.edu.agro.gateway.config.Roles.PRODUCTOR;

/**
 * Security Filter del Gateway (reactivo): valida el JWT de Keycloak y autoriza por método, ruta y rol.
 *
 * El gateway decide QUÉ ROL puede llamar a QUÉ endpoint. La regla de pertenencia (C6: un productor
 * solo ve lo suyo) NO se resuelve acá: necesita datos del negocio y la aplica cada microservicio.
 *
 * Reglas: se evalúan en orden y gana la primera que coincide, por eso las rutas más específicas
 * (monitoreos, autorizar/rechazar aplicaciones) van antes que las generales. Lo que no coincide
 * con ninguna se rechaza (denyAll): un endpoint nuevo queda cerrado hasta que se lo agrega acá.
 * La matriz completa está en gateway/README.md.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] RUTAS_CAMPOS = {
            "/api/v1/establecimientos/**", "/api/v1/lotes/**", "/api/v1/campanias/**", "/api/v1/aplicaciones/**"};
    private static final String[] CATALOGO = {
            "/api/v1/cultivos/**", "/api/v1/plagas/**", "/api/v1/agroquimicos/**"};

    @Bean
    SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(ex -> ex
                .pathMatchers("/actuator/health").permitAll()

                // Lectura: cualquier usuario autenticado con un rol del dominio
                // (qué datos ve cada uno lo filtra el microservicio, regla C6)
                .pathMatchers(HttpMethod.GET, RUTAS_CAMPOS).hasAnyRole(PRODUCTOR, OPERARIO, AGRONOMO)
                // Consultar una evaluación pasada (F13): solo el AGRONOMO. Va antes que la lectura general.
                .pathMatchers(HttpMethod.GET, "/api/v1/evaluaciones/**").hasRole(AGRONOMO)
                .pathMatchers(HttpMethod.GET, CATALOGO).hasAnyRole(PRODUCTOR, OPERARIO, AGRONOMO)

                // Catálogo (cultivos, plagas, agroquímicos): solo lo administra el AGRONOMO
                .pathMatchers(HttpMethod.POST, CATALOGO).hasRole(AGRONOMO)
                .pathMatchers(HttpMethod.PUT, CATALOGO).hasRole(AGRONOMO)
                .pathMatchers(HttpMethod.PATCH, CATALOGO).hasRole(AGRONOMO)
                .pathMatchers(HttpMethod.DELETE, CATALOGO).hasRole(AGRONOMO)

                // Evaluaciones: las pide ms-campos (con el token del usuario); por el gateway pueden consultarlas los tres roles
                .pathMatchers(HttpMethod.POST, "/api/v1/evaluaciones/**").hasAnyRole(PRODUCTOR, OPERARIO, AGRONOMO)

                // Monitoreos (/campanias/{id}/monitoreos): los registran PRODUCTOR y OPERARIO.
                // Van ANTES que las reglas de campañas.
                .pathMatchers(HttpMethod.POST, "/api/v1/campanias/*/monitoreos/**").hasAnyRole(PRODUCTOR, OPERARIO)
                .pathMatchers(HttpMethod.PUT, "/api/v1/campanias/*/monitoreos/**").hasRole(PRODUCTOR)
                .pathMatchers(HttpMethod.PATCH, "/api/v1/campanias/*/monitoreos/**").hasRole(PRODUCTOR)
                .pathMatchers(HttpMethod.DELETE, "/api/v1/campanias/*/monitoreos/**").hasRole(PRODUCTOR)

                // Aplicaciones (T07): se registran en una campaña (POST /campanias/{id}/aplicaciones), las registran
                // PRODUCTOR y OPERARIO. Autorizar y reevaluar una pendiente es del AGRONOMO.
                .pathMatchers(HttpMethod.POST, "/api/v1/campanias/*/aplicaciones").hasAnyRole(PRODUCTOR, OPERARIO)
                .pathMatchers(HttpMethod.POST, "/api/v1/aplicaciones/*/autorizacion", "/api/v1/aplicaciones/*/reevaluacion").hasRole(AGRONOMO)

                // Establecimientos y campañas: los gestiona el PRODUCTOR
                .pathMatchers(HttpMethod.POST, "/api/v1/establecimientos/**", "/api/v1/campanias/**").hasRole(PRODUCTOR)
                .pathMatchers(HttpMethod.PUT, "/api/v1/establecimientos/**", "/api/v1/campanias/**").hasRole(PRODUCTOR)
                .pathMatchers(HttpMethod.PATCH, "/api/v1/establecimientos/**", "/api/v1/campanias/**").hasRole(PRODUCTOR)
                .pathMatchers(HttpMethod.DELETE, "/api/v1/establecimientos/**", "/api/v1/campanias/**").hasRole(PRODUCTOR)

                // Lotes: el suelo (cargar a mano o refrescar desde las APIs externas) es del AGRONOMO;
                // planificar una campaña en el lote es del PRODUCTOR. Los lotes se crean por
                // POST /establecimientos/{id}/lotes (regla de establecimientos, arriba).
                .pathMatchers(HttpMethod.PATCH, "/api/v1/lotes/*/suelo").hasRole(AGRONOMO)
                .pathMatchers(HttpMethod.POST, "/api/v1/lotes/*/suelo/refrescar").hasRole(AGRONOMO)
                .pathMatchers(HttpMethod.POST, "/api/v1/lotes/*/campanias").hasRole(PRODUCTOR)

                // Todo lo demás (incluido cualquier endpoint nuevo no listado): rechazado
                .anyExchange().denyAll())
            .oauth2ResourceServer(o -> o
                .jwt(j -> j.jwtAuthenticationConverter(KeycloakJwtConverter.build()))
                // T12: Keycloak caído = 503, no 401
                .authenticationEntryPoint(new IdentidadNoDisponibleEntryPoint()));
        return http.build();
    }
}
