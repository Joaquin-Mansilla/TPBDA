package ar.edu.agro.campos.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cliente de ms-campos hacia ms-tratamientos. Se define en el application.yml bajo "agro.tratamientos".
 *
 * reintentosLectura se aplica solo a las consultas (GET). El pedido de evaluación (POST) no se reintenta
 * nunca: Tratamientos guarda cada evaluación, así que repetirlo tras un timeout podría duplicar el registro.
 */
@Validated
@ConfigurationProperties(prefix = "agro.tratamientos")
public record TratamientosProperties(
        @NotBlank String urlBase,
        @NotNull Duration timeoutConexion,
        @NotNull Duration timeoutLectura,
        @Min(0) int reintentosLectura) {
}
