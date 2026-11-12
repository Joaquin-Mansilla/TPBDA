package ar.edu.agro.campos.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de las APIs públicas que consulta ms-campos al crear un lote.
 * Se define en el application.yml bajo "agro.externas" (ver docs/clientes-externos.md).
 *
 * habilitado=false (perfil h2, o sin Internet) no consulta ninguna API: los lotes nuevos quedan SIN_DATOS
 * y el agrónomo carga el suelo a mano.
 */
@Validated
@ConfigurationProperties(prefix = "agro.externas")
public record ApisExternasProperties(
        @DefaultValue("true") boolean habilitado,
        @Valid @NotNull SoilGrids soilgrids,
        @Valid @NotNull Georef georef,
        @Valid @NotNull Textura textura) {

    /** SoilGrids (ISRIC): propiedades del suelo por coordenadas. */
    public record SoilGrids(
            @NotBlank String urlBase,
            @NotBlank String profundidad,
            @NotNull Duration timeoutConexion,
            @NotNull Duration timeoutLectura,
            @Min(0) int reintentos) {
    }

    /** Georef (datos.gob.ar): ubicación administrativa por coordenadas. */
    public record Georef(
            @NotBlank String urlBase,
            @NotNull Duration timeoutConexion,
            @NotNull Duration timeoutLectura,
            @Min(0) int reintentos) {
    }

    /** Umbrales de la regla simplificada de clase de textura (son datos, no están fijos en el código). */
    public record Textura(
            @NotNull BigDecimal umbralArenaPct,
            @NotNull BigDecimal umbralArcillaPct) {
    }
}
