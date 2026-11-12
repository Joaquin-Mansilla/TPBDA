package ar.edu.agro.tratamientos.model;

import ar.edu.agro.commons.contract.ResultadoEvaluacion;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cada decisión tomada. Guarda una copia (snapshot) del pedido recibido: si después cambia el catálogo,
 * el registro de por qué se decidió algo no cambia. Los ids de catálogo se guardan como columnas simples.
 */
@Entity
@Table(name = "evaluacion")
@Getter @Setter @NoArgsConstructor
public class Evaluacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "fecha_evaluacion", nullable = false)
    private LocalDateTime fechaEvaluacion;
    @Column(name = "agroquimico_id", nullable = false)
    private Long agroquimicoId;
    @Column(name = "cultivo_id", nullable = false)
    private Long cultivoId;
    @Column(name = "plaga_id", nullable = false)
    private Long plagaId;
    @Column(name = "fecha_aplicacion", nullable = false)
    private LocalDate fechaAplicacion;
    @Column(name = "dosis_por_ha", nullable = false, precision = 10, scale = 3)
    private BigDecimal dosisPorHa;
    @Column(name = "superficie_ha", nullable = false, precision = 10, scale = 2)
    private BigDecimal superficieHa;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ResultadoEvaluacion resultado;
    @Column(name = "fecha_sugerida")
    private LocalDate fechaSugerida;
    @Column(name = "dosis_total", precision = 12, scale = 3)
    private BigDecimal dosisTotal;
    private Integer envases;
    @Column(precision = 14, scale = 2)
    private BigDecimal costo;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "snapshot_pedido", nullable = false)
    private String snapshotPedido;
    @Column(name = "solicitada_por_sub", nullable = false, length = 36)
    private String solicitadaPorSub;
    @OneToMany(mappedBy = "evaluacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EvaluacionMotivo> motivos = new ArrayList<>();
}
