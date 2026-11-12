package ar.edu.agro.tratamientos.dto;

import jakarta.validation.constraints.*;

public record CultivoRequest(
        @NotBlank @Size(max = 80) String nombre,
        @NotBlank @Size(max = 60) String familia,
        @NotNull @Positive Integer diasCiclo,
        @NotNull @Min(1) @Max(12) Integer mesSiembraDesde,
        @NotNull @Min(1) @Max(12) Integer mesSiembraHasta) {
}
