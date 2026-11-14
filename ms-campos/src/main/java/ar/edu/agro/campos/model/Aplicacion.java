package ar.edu.agro.campos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Aplicación de un agroquímico. Es también el historial que usa la regla T3 de ms-tratamientos. */
@Entity
@Table(name = "aplicacion")
@Getter @Setter @NoArgsConstructor
public class Aplicacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "campania_id")
    private Campania campania;
    @Column(name = "agroquimico_id", nullable = false)
    private Long agroquimicoId;
    @Column(name = "plaga_id", nullable = false)
    private Long plagaId;
    @Column(name = "dosis_por_ha", nullable = false, precision = 10, scale = 3)
    private BigDecimal dosisPorHa;
    @Column(name = "superficie_tratada_ha", nullable = false, precision = 10, scale = 2)
    private BigDecimal superficieTratadaHa;
    @Column(nullable = false)
    private LocalDate fecha;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 25)
    private EstadoAplicacion estado;
    @Column(name = "evaluacion_id")
    private Long evaluacionId;
    /** Datos de la última evaluación de ms-tratamientos, para consultar la aplicación sin volver a llamarlo (F08). */
    @Column(name = "dosis_total", precision = 12, scale = 3)
    private BigDecimal dosisTotal;
    @Column
    private Integer envases;
    @Column(precision = 14, scale = 2)
    private BigDecimal costo;
    @Column(length = 500)
    private String justificacion;
    @Column(name = "autorizada_por_sub", length = 36)
    private String autorizadaPorSub;
    @Column(name = "registrada_por_sub", nullable = false, length = 36)
    private String registradaPorSub;
    @Version @Column(nullable = false)
    private Long version;
}
