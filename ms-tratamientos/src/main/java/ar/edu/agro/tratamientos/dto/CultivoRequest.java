package ar.edu.agro.tratamientos.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** diasEntreRiegos y mmPorRiego son opcionales y van juntos: sin ellos el cultivo se hace a secano. */
public record CultivoRequest(
        @NotBlank @Size(max = 80) String nombre,
        @NotBlank @Size(max = 60) String familia,
        @NotNull @Positive Integer diasCiclo,
        @NotNull @Min(1) @Max(12) Integer mesSiembraDesde,
        @NotNull @Min(1) @Max(12) Integer mesSiembraHasta,
        @Positive @Max(60) Integer diasEntreRiegos,
        @Positive @DecimalMax("200") BigDecimal mmPorRiego) {
}
