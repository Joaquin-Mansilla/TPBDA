package ar.edu.agro.tratamientos.config;

import ar.edu.agro.commons.security.KeycloakJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static ar.edu.agro.commons.security.Roles.*;

/** Resource Server: el servicio vuelve a validar el JWT aunque el pedido haya pasado por el gateway. */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // consultar una evaluación pasada: solo AGRONOMO (F13)
                        .requestMatchers(HttpMethod.GET, "/api/v1/evaluaciones/**").hasRole(AGRONOMO)
                        // pedir una evaluación: cualquier rol, vía ms-campos
                        .requestMatchers(HttpMethod.POST, "/api/v1/evaluaciones").hasAnyRole(PRODUCTOR, OPERARIO, AGRONOMO)
                        // consultar el catálogo: cualquier rol
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole(PRODUCTOR, OPERARIO, AGRONOMO)
                        // administrar el catálogo: solo AGRONOMO
                        .requestMatchers("/api/v1/**").hasRole(AGRONOMO)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(KeycloakJwtAuthenticationConverter.build()))
                        // T12: Keycloak caído = 503, no 401
                        .authenticationEntryPoint(new IdentidadNoDisponibleEntryPoint()));
        return http.build();
    }
}
