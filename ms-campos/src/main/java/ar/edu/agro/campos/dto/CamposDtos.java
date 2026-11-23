package ar.edu.agro.campos.dto;

import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.commons.contract.MotivoDto;
import ar.edu.agro.commons.contract.ResultadoEvaluacion;
import ar.edu.agro.commons.contract.SueloDto;
import ar.edu.agro.campos.model.EstadoAplicacion;
import ar.edu.agro.campos.model.EstadoCampania;
import ar.edu.agro.campos.model.EstadoRiego;
import ar.edu.agro.campos.model.EstadoSuelo;
import ar.edu.agro.campos.model.OrigenAgua;
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
    /** El establecimiento se define con un rectángulo de coordenadas; con él se calculan sus hectáreas (T16). */
    public record EstablecimientoRequest(@NotBlank @Size(max = 100) String nombre, Set<String> operarios,
                                         @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitudMin,
                                         @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitudMax,
                                         @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitudMin,
                                         @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitudMax) {
    }

    public record LoteResumen(Long id, String nombre, BigDecimal superficieHa, EstadoSuelo estadoSuelo) {
    }

    /** superficieLibreHa = superficie del establecimiento menos la suma de sus lotes. */
    public record EstablecimientoResponse(Long id, String nombre, String productorSub, String productorNombre,
                                          Set<String> operarios,
                                          List<LoteResumen> lotes, BigDecimal latitudMin, BigDecimal latitudMax,
                                          BigDecimal longitudMin, BigDecimal longitudMax, BigDecimal superficieHa,
                                          BigDecimal superficieLibreHa) {
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

    /**
     * fechaSiembra es la planificada; fechaSiembraReal, fechaCosecha y fechaPerdida se completan con cada acción.
     * kgCosechados y rendimientoKgHa (kg / superficie sembrada) se completan al cosechar, si se informan los kg.
     */
    public record CampaniaResponse(Long id, Long loteId, Long cultivoId, BigDecimal superficieSembradaHa,
                                   LocalDate fechaSiembra, LocalDate cosechaEstimada, EstadoCampania estado,
                                   String motivoPerdida, LocalDate fechaSiembraReal, LocalDate fechaCosecha,
                                   LocalDate fechaPerdida, BigDecimal kgCosechados, BigDecimal rendimientoKgHa) {
    }

    // ---- acciones sobre la campaña (el estado cambia como consecuencia, C5)
    public record SiembraRequest(@NotNull LocalDate fecha) {
    }

    /** kgCosechados es opcional: con él se calcula el rendimiento (kg/ha). */
    public record CosechaRequest(@NotNull LocalDate fecha, @Positive BigDecimal kgCosechados) {
        public CosechaRequest(LocalDate fecha) {
            this(fecha, null);
        }
    }

    public record PerdidaRequest(@NotNull LocalDate fecha, @NotBlank @Size(max = 255) String motivo) {
    }

    public record MonitoreoRequest(@NotNull Long plagaId,
                                   @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal incidenciaPct,
                                   LocalDate fecha) {
    }

    /** alerta = la incidencia llegó al umbral (agro.monitoreos.umbral-alerta-pct): se recomienda una aplicación (M1). */
    public record MonitoreoResponse(Long id, Long campaniaId, Long plagaId, BigDecimal incidenciaPct, LocalDate fecha,
                                    String registradoPorSub, String registradoPorNombre, boolean alerta, String mensaje) {
    }

    /**
     * Un monitoreo que llegó al umbral (M1). Queda atendida cuando hay una aplicación hecha (REGISTRADA o
     * AUTORIZADA_MANUALMENTE) contra la misma plaga en la campaña, con fecha igual o posterior al monitoreo.
     */
    public record AlertaResponse(Long monitoreoId, Long plagaId, BigDecimal incidenciaPct, LocalDate fecha,
                                 boolean atendida, Long aplicacionId, String mensaje) {
    }

    // ---- riego (R1)
    /** Sin fecha, es hoy. */
    public record RiegoRequest(LocalDate fecha, @NotNull @Positive @DecimalMax("200") BigDecimal laminaMm) {
    }

    public record RiegoResponse(Long id, Long campaniaId, LocalDate fecha, BigDecimal laminaMm, String registradoPorSub,
                                String registradoPorNombre) {
    }

    /** Lluvia del lote cargada a mano. Sin fecha, es hoy; 0 mm deja registrado que no llovió. */
    public record LluviaRequest(LocalDate fecha, @NotNull @DecimalMin("0") @DecimalMax("500") BigDecimal mm) {
    }

    public record LluviaResponse(Long id, Long loteId, LocalDate fecha, BigDecimal mm, String registradoPorSub,
                                 String registradoPorNombre) {
    }

    /**
     * Estado de riego de una campaña (R1). Se calcula al consultarlo con los riegos de la campaña y las lluvias
     * cargadas en el lote.
     * mmFaltantes = cuánto hay que regar para completar el riego (mmPorRiego menos lo que ya juntó entre riego y
     * lluvia desde la última agua completa).
     * Con NO_REQUIERE o NO_SEMBRADA, los campos del cálculo van en null.
     */
    public record EstadoRiegoResponse(Long campaniaId, Long cultivoId, String cultivo, EstadoRiego estado,
                                      String mensaje, Integer diasEntreRiegos, BigDecimal mmPorRiego,
                                      LocalDate ultimaAgua, OrigenAgua origenUltimaAgua, Long diasSinAgua,
                                      LocalDate proximoRiego, BigDecimal mmDesdeUltimaAgua, BigDecimal mmFaltantes,
                                      BigDecimal lluviaDesdeSiembraMm, int riegosRegistrados,
                                      BigDecimal mmRegadosTotal) {
    }

    // ---- historial del lote
    public record HistorialLoteResponse(Long loteId, String nombre, BigDecimal superficieHa,
                                        List<CampaniaHistorial> campanias, List<RendimientoCultivo> rendimientos) {
    }

    /** fechaSiembra es la real si ya se sembró; si no, la planificada. cultivo es null si ms-tratamientos no respondió. */
    public record CampaniaHistorial(Long id, Long cultivoId, String cultivo, EstadoCampania estado,
                                    BigDecimal superficieSembradaHa, LocalDate fechaSiembra, LocalDate cosechaEstimada,
                                    LocalDate fechaCosecha, BigDecimal kgCosechados, BigDecimal rendimientoKgHa,
                                    int monitoreos, int alertasPendientes, int aplicaciones, int riegos,
                                    BigDecimal mmRegados) {
    }

    /** Promedio ponderado por hectárea de las campañas cosechadas con kg informados. */
    public record RendimientoCultivo(Long cultivoId, String cultivo, int campaniasCosechadas, BigDecimal haCosechadas,
                                     BigDecimal kgTotales, BigDecimal rendimientoPromedioKgHa) {
    }

    // ---- aplicaciones
    public record AplicacionRequest(@NotNull Long agroquimicoId, @NotNull Long plagaId,
                                    @NotNull @Positive BigDecimal dosisPorHa,
                                    @NotNull @Positive BigDecimal superficieTratadaHa, @NotNull LocalDate fecha) {
    }

    public record AplicacionResponse(Long id, Long campaniaId, Long agroquimicoId, Long plagaId, BigDecimal dosisPorHa,
                                     BigDecimal superficieTratadaHa, LocalDate fecha, EstadoAplicacion estado,
                                     Long evaluacionId, BigDecimal dosisTotal, Integer envases, BigDecimal costo,
                                     String justificacion, String autorizadaPorSub, String autorizadaPorNombre,
                                     String registradaPorSub, String registradaPorNombre) {
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
