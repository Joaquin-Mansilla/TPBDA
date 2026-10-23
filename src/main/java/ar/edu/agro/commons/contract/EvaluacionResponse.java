package ar.edu.agro.commons.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Decisión de Tratamientos. {@code fechaSugerida} solo se completa en REPROGRAMAR;
 * {@code dosisTotal}, {@code envases} y {@code costo} se informan cuando se pudieron calcular.
 */
public record EvaluacionResponse(
        Long id,
        ResultadoEvaluacion resultado,
        List<MotivoDto> motivos,
        LocalDate fechaSugerida,
        BigDecimal dosisTotal,
        Integer envases,
        BigDecimal costo) {

    public EvaluacionResponse {
        motivos = motivos == null ? List.of() : List.copyOf(motivos);
    }
}
