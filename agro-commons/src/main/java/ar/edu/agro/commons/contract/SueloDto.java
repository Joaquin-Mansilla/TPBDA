package ar.edu.agro.commons.contract;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Suelo del lote. Los campos pueden venir en null cuando el lote está en estado SIN_DATOS:
 * en ese caso Tratamientos responde REVISION_MANUAL.
 */
public record SueloDto(
        ClaseTextura claseTextura,
        @DecimalMin("0") @DecimalMax("100") BigDecimal arcillaPct,
        @DecimalMin("0") @DecimalMax("100") BigDecimal arenaPct,
        @DecimalMin("0") @DecimalMax("100") BigDecimal limoPct,
        @DecimalMin("0") @DecimalMax("14") BigDecimal ph,
        @DecimalMin("0") BigDecimal carbonoOrganicoGKg) {

    /** Para T2 alcanza con la clase de textura. */
    public boolean tieneTextura() {
        return claseTextura != null;
    }
}
