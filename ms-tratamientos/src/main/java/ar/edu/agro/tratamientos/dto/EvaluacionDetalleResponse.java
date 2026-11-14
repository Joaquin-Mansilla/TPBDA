package ar.edu.agro.tratamientos.dto;

import ar.edu.agro.commons.contract.EvaluacionResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Evaluación pasada con el pedido que se usó para decidir (F13). */
public record EvaluacionDetalleResponse(EvaluacionResponse evaluacion, LocalDateTime fechaEvaluacion,
                                        Long agroquimicoId, Long cultivoId, Long plagaId, LocalDate fechaAplicacion,
                                        BigDecimal dosisPorHa, BigDecimal superficieHa, String solicitadaPorSub,
                                        JsonNode snapshotPedido) {
}
