package ar.edu.agro.commons.contract;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Aplicación anterior del mismo producto en el mismo lote (historial que guarda Campos). Se usa en T3. */
public record AplicacionPreviaDto(@NotNull LocalDate fecha) {
}
