package ar.edu.agro.campos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "campania")
@Getter @Setter @NoArgsConstructor
public class Campania {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "lote_id")
    private Lote lote;
    /** referencia lógica a tratamientos.cultivo */
    @Column(name = "cultivo_id", nullable = false)
    private Long cultivoId;
    @Column(name = "superficie_sembrada_ha", nullable = false, precision = 10, scale = 2)
    private BigDecimal superficieSembradaHa;
    @Column(name = "fecha_siembra", nullable = false)
    private LocalDate fechaSiembra;
    @Column(name = "cosecha_estimada", nullable = false)
    private LocalDate cosechaEstimada;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private EstadoCampania estado = EstadoCampania.PLANIFICADA;
    @Column(name = "motivo_perdida")
    private String motivoPerdida;
    @Version @Column(nullable = false)
    private Long version;
}
