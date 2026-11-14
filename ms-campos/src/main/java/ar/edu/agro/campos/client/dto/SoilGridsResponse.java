package ar.edu.agro.campos.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * Parte de la respuesta de SoilGrids v2.0 (properties/query) que usa el sistema.
 * Cada capa trae el valor como entero y un factor de conversión (d_factor): valor real = value / d_factor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SoilGridsResponse(Properties properties) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(List<Layer> layers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Layer(
            String name,
            @JsonProperty("unit_measure") UnitMeasure unitMeasure,
            List<Depth> depths) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UnitMeasure(@JsonProperty("d_factor") BigDecimal dFactor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Depth(String label, Values values) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Values(BigDecimal mean) {
    }
}
