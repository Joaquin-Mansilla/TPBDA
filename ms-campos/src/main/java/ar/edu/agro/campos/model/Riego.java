package ar.edu.agro.campos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un riego de la campaña: cuántos mm de agua se aplicaron y cuándo. Con ellos (y la lluvia) se calcula R1. */
@Entity
@Table(name = "riego")
@Getter @Setter @NoArgsConstructor
public class Riego {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "campania_id")
    private Campania campania;
    @Column(nullable = false)
    private LocalDate fecha;
    @Column(name = "lamina_mm", nullable = false, precision = 6, scale = 1)
    private BigDecimal laminaMm;
    @Column(name = "registrado_por_sub", nullable = false, length = 36)
    private String registradoPorSub;
    @Column(name = "registrado_por_nombre", length = 100)
    private String registradoPorNombre;
}
