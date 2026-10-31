package ar.edu.agro.commons.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Pedido que Campos envía a Tratamientos (POST /api/v1/evaluaciones).
 * Tratamientos no conoce lotes ni campañas: decide solo con estos datos.
 */
public record EvaluacionRequest(
        @NotNull Long cultivoId,
        @NotNull Long plagaId,
        @NotNull Long agroquimicoId,
        @NotNull @Positive BigDecimal dosisPorHa,
        @NotNull @Positive BigDecimal superficieTratadaHa,
        @NotNull LocalDate fechaAplicacion,
        @NotNull LocalDate cosechaEstimada,
        @Valid SueloDto suelo,
        @Valid UbicacionDto ubicacion,
        @Valid List<AplicacionPreviaDto> aplicacionesPrevias) {

    public EvaluacionRequest {
        aplicacionesPrevias = aplicacionesPrevias == null ? List.of() : List.copyOf(aplicacionesPrevias);
    }
}
