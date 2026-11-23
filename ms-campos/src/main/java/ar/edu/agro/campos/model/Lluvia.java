package ar.edu.agro.campos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lluvia de un día en el lote, cargada a mano (por ejemplo, lo que marcó el pluviómetro). Es del lote y no de la
 * campaña porque llueve sobre todo el lote: cuenta para todas las campañas que estén sembradas en él (R1).
 * 0 mm es válido: deja registrado que ese día no llovió.
 */
@Entity
@Table(name = "lluvia")
@Getter @Setter @NoArgsConstructor
public class Lluvia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "lote_id")
    private Lote lote;
    @Column(nullable = false)
    private LocalDate fecha;
    @Column(nullable = false, precision = 6, scale = 1)
    private BigDecimal mm;
    @Column(name = "registrado_por_sub", nullable = false, length = 36)
    private String registradoPorSub;
    @Column(name = "registrado_por_nombre", length = 100)
    private String registradoPorNombre;
}
