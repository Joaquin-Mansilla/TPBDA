package ar.edu.agro.tratamientos.dto;

import ar.edu.agro.commons.contract.ClaseTextura;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record AgroquimicoRequest(
        @NotBlank @Size(max = 80) String nombre,
        @NotBlank @Size(max = 80) String principioActivo,
        @NotNull @Positive BigDecimal dosisMinHa,
        @NotNull @Positive BigDecimal dosisMaxHa,
        @NotBlank @Pattern(regexp = "l|kg", message = "la unidad debe ser l o kg") String unidad,
        @NotNull @Positive BigDecimal contenidoEnvase,
        @NotNull @PositiveOrZero BigDecimal precioEnvase,
        @NotNull @PositiveOrZero Integer diasCarencia,
        @NotNull @PositiveOrZero Integer intervaloMinDias,
        @Valid List<HabilitacionDto> habilitaciones,
        @Valid List<AjusteSueloDto> ajustesSuelo,
        @Valid List<RestriccionDto> restricciones) {

    @JsonIgnore
    @AssertTrue(message = "la dosis mínima no puede ser mayor que la máxima")
    public boolean isDosisCoherentes() {
        return dosisMinHa == null || dosisMaxHa == null || dosisMinHa.compareTo(dosisMaxHa) <= 0;
    }

    public record HabilitacionDto(@NotNull Long cultivoId, @NotNull Long plagaId) {
    }

    public record AjusteSueloDto(@NotNull ClaseTextura claseTextura,
                                 @NotNull @Positive @DecimalMax("9.99") BigDecimal factorDosisMax) {
    }

    public record RestriccionDto(@NotBlank String provincia, String departamento) {
    }
}
