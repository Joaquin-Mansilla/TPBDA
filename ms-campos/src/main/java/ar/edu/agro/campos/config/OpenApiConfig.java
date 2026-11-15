package ar.edu.agro.campos.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Swagger UI (T14): datos del servicio y botón Authorize para probar los endpoints con el JWT de Keycloak. */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "ms-campos", version = "1.0",
                description = "Establecimientos, lotes, campañas, monitoreos y aplicaciones (reglas C1-C6, L1, A1)"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "Token de Keycloak (realm agro). Pegar solo el token, sin la palabra Bearer.")
public class OpenApiConfig {
}
