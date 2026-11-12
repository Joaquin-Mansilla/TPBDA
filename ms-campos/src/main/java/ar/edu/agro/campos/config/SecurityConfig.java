package ar.edu.agro.campos.config;

import ar.edu.agro.commons.security.KeycloakJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static ar.edu.agro.commons.security.Roles.*;

/**
 * Resource Server: vuelve a validar el JWT aunque el pedido haya pasado por el gateway. El rol por operación se
 * controla con @PreAuthorize en cada controlador y la pertenencia (C6) con AccesoService, comparando el sub del token.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/**").hasAnyRole(PRODUCTOR, OPERARIO, AGRONOMO)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(KeycloakJwtAuthenticationConverter.build()))
                        // T12: Keycloak caído = 503, no 401
                        .authenticationEntryPoint(new IdentidadNoDisponibleEntryPoint()));
        return http.build();
    }
}
