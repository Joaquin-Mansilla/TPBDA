package ar.edu.agro.tratamientos.service;

import ar.edu.agro.commons.contract.MotivoDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reglas T1 a T5 y cálculo de dosis total, envases y costo, sin base de datos ni Spring:
 * reciben valores y devuelven si se cumple o no, para poder probarlas con casos normales, límites y rechazos.
 */
public final class ReglasEvaluacion {

    public record Costo(BigDecimal dosisTotal, int envases, BigDecimal costo) {
    }

    public record Restriccion(String provincia, String departamento) {
    }

    private ReglasEvaluacion() {
    }

    /** T1: el producto debe estar habilitado para ese cultivo y esa plaga. */
    public static Optional<MotivoDto> t1Habilitacion(boolean habilitado) {
        return habilitado ? Optional.empty()
                : Optional.of(new MotivoDto("T1", "El producto no está habilitado para ese cultivo y esa plaga"));
    }

    /** T2: la dosis debe estar entre la mínima y la máxima, con la máxima ajustada por la textura del suelo. */
    public static Optional<MotivoDto> t2Dosis(BigDecimal dosis, BigDecimal dosisMin, BigDecimal dosisMax, BigDecimal factor) {
        BigDecimal maximaAjustada = dosisMax.multiply(factor == null ? BigDecimal.ONE : factor);
        if (dosis.compareTo(maximaAjustada) > 0) {
            return Optional.of(new MotivoDto("T2", "La dosis " + dosis.stripTrailingZeros().toPlainString()
                    + " supera la máxima para este suelo (" + maximaAjustada.stripTrailingZeros().toPlainString() + ")"));
        }
        if (dosis.compareTo(dosisMin) < 0) {
            return Optional.of(new MotivoDto("T2", "La dosis " + dosis.stripTrailingZeros().toPlainString()
                    + " es menor que la mínima (" + dosisMin.stripTrailingZeros().toPlainString() + ")"));
        }
        return Optional.empty();
    }

    /**
     * T3: debe pasar el intervalo mínimo desde la última aplicación del mismo producto.
     * Devuelve la fecha sugerida (última + intervalo) si no se cumple.
     */
    public static Optional<LocalDate> t3Intervalo(LocalDate fechaAplicacion, LocalDate ultimaAplicacion, int intervaloMinDias) {
        if (ultimaAplicacion == null) {
            return Optional.empty();
        }
        LocalDate primeraFechaPosible = ultimaAplicacion.plusDays(intervaloMinDias);
        return fechaAplicacion.isBefore(primeraFechaPosible) ? Optional.of(primeraFechaPosible) : Optional.empty();
    }

    /** T4: la carencia debe terminar antes (o el mismo día) de la cosecha estimada. */
    public static Optional<MotivoDto> t4Carencia(LocalDate fechaAplicacion, int diasCarencia, LocalDate cosechaEstimada) {
        LocalDate finCarencia = fechaAplicacion.plusDays(diasCarencia);
        return finCarencia.isAfter(cosechaEstimada)
                ? Optional.of(new MotivoDto("T4", "La carencia termina el " + finCarencia
                + ", después de la cosecha estimada (" + cosechaEstimada + ")"))
                : Optional.empty();
    }

    /** T5: el producto no se puede aplicar en las provincias (o departamentos) restringidas. */
    public static Optional<MotivoDto> t5Restriccion(String provincia, String departamento, List<Restriccion> restricciones) {
        for (Restriccion r : restricciones) {
            boolean mismaProvincia = r.provincia() != null && r.provincia().equalsIgnoreCase(provincia);
            boolean todaLaProvincia = r.departamento() == null || r.departamento().isBlank();
            boolean mismoDepartamento = !todaLaProvincia && r.departamento().equalsIgnoreCase(departamento);
            if (mismaProvincia && (todaLaProvincia || mismoDepartamento)) {
                return Optional.of(new MotivoDto("T5", "El producto está restringido en "
                        + (todaLaProvincia ? r.provincia() : r.departamento() + ", " + r.provincia())));
            }
        }
        return Optional.empty();
    }

    /** Dosis total, envases (se redondea hacia arriba: no se compran envases a medias) y costo. */
    public static Costo calcularCosto(BigDecimal dosisPorHa, BigDecimal superficieHa, BigDecimal contenidoEnvase,
                                      BigDecimal precioEnvase) {
        BigDecimal dosisTotal = dosisPorHa.multiply(superficieHa).setScale(3, RoundingMode.HALF_UP);
        int envases = dosisTotal.divide(contenidoEnvase, 0, RoundingMode.CEILING).intValueExact();
        BigDecimal costo = precioEnvase.multiply(BigDecimal.valueOf(envases)).setScale(2, RoundingMode.HALF_UP);
        return new Costo(dosisTotal, envases, costo);
    }
}
