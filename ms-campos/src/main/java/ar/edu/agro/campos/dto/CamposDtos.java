package ar.edu.agro.campos.dto;

import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.commons.contract.MotivoDto;
import ar.edu.agro.commons.contract.ResultadoEvaluacion;
import ar.edu.agro.commons.contract.SueloDto;
import ar.edu.agro.campos.model.EstadoAplicacion;
import ar.edu.agro.campos.model.EstadoCampania;
import ar.edu.agro.campos.model.EstadoSuelo;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** DTOs propios de la API de ms-campos (no son contratos compartidos, por eso no están en agro-commons). */
public final class CamposDtos {
    private CamposDtos() {
    }

    // ---- establecimientos y lotes
    public record EstablecimientoRequest(@NotBlank @Size(max = 100) String nombre, Set<String> operarios) {
    }

    public record LoteResumen(Long id, String nombre, BigDecimal superficieHa, EstadoSuelo estadoSuelo) {
    }

    public record EstablecimientoResponse(Long id, String nombre, String productorSub, Set<String> operarios,
                                          List<LoteResumen> lotes) {
    }

    public record LoteRequest(@NotBlank @Size(max = 100) String nombre,
                              @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
                              @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud,
                              @NotNull @Positive BigDecimal superficieHa) {
    }

    public record LoteResponse(Long id, Long establecimientoId, String nombre, BigDecimal latitud, BigDecimal longitud,
                               BigDecimal superficieHa, String provincia, String departamento, EstadoSuelo estadoSuelo,
                               SueloDto suelo) {
    }

    /** Carga manual del suelo (F03): o la clase de textura, o los tres porcentajes para calcularla. */
    public record SueloRequest(ClaseTextura claseTextura,
                               @DecimalMin("0") @DecimalMax("100") BigDecimal arcillaPct,
                               @DecimalMin("0") @DecimalMax("100") BigDecimal arenaPct,
                               @DecimalMin("0") @DecimalMax("100") BigDecimal limoPct,
                               @DecimalMin("0") @DecimalMax("14") BigDecimal ph,
                               @DecimalMin("0") BigDecimal carbonoOrganico,
                               String provincia, String departamento) {
    }

    // ---- campañas
    public record CampaniaRequest(@NotNull Long cultivoId, @NotNull @Positive BigDecimal superficieSembradaHa,
                                  @NotNull LocalDate fechaSiembra) {
    }

    public record CampaniaResponse(Long id, Long loteId, Long cultivoId, BigDecimal superficieSembradaHa,
                                   LocalDate fechaSiembra, LocalDate cosechaEstimada, EstadoCampania estado,
                                   String motivoPerdida) {
    }

    public record CambioEstadoRequest(@NotNull EstadoCampania estado, @Size(max = 255) String motivo) {
    }

    public record MonitoreoRequest(@NotNull Long plagaId,
                                   @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal incidenciaPct,
                                   LocalDate fecha) {
    }

    public record MonitoreoResponse(Long id, Long campaniaId, Long plagaId, BigDecimal incidenciaPct, LocalDate fecha) {
    }

    // ---- aplicaciones
    public record AplicacionRequest(@NotNull Long agroquimicoId, @NotNull Long plagaId,
                                    @NotNull @Positive BigDecimal dosisPorHa,
                                    @NotNull @Positive BigDecimal superficieTratadaHa, @NotNull LocalDate fecha) {
    }

    public record AplicacionResponse(Long id, Long campaniaId, Long agroquimicoId, Long plagaId, BigDecimal dosisPorHa,
                                     BigDecimal superficieTratadaHa, LocalDate fecha, EstadoAplicacion estado,
                                     Long evaluacionId, BigDecimal dosisTotal, Integer envases, BigDecimal costo,
                                     String justificacion, String autorizadaPorSub, String registradaPorSub) {
    }

    public record AutorizacionRequest(@NotBlank @Size(max = 500) String justificacion) {
    }

    /**
     * Resultado del proceso principal (F07). aplicacionId es null cuando se rechaza (no se guarda).
     * resultado es null cuando ms-tratamientos no respondió y la aplicación quedó pendiente.
     */
    public record AplicacionResultadoResponse(Long aplicacionId, EstadoAplicacion estado, ResultadoEvaluacion resultado,
                                              Long evaluacionId, LocalDate fechaSugerida, BigDecimal dosisTotal,
                                              Integer envases, BigDecimal costo, List<MotivoDto> motivos,
                                              String mensaje) {
    }
}
