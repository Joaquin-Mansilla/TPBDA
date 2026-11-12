package ar.edu.agro.campos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "monitoreo")
@Getter @Setter @NoArgsConstructor
public class Monitoreo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "campania_id")
    private Campania campania;
    @Column(name = "plaga_id", nullable = false)
    private Long plagaId;
    @Column(name = "incidencia_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal incidenciaPct;
    @Column(nullable = false)
    private LocalDate fecha;
}
