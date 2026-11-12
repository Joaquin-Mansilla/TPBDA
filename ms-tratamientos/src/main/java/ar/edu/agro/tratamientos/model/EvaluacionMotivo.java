package ar.edu.agro.tratamientos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evaluacion_motivo")
@Getter @Setter @NoArgsConstructor
public class EvaluacionMotivo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "evaluacion_id")
    private Evaluacion evaluacion;
    @Column(nullable = false, length = 5)
    private String regla;
    @Column(nullable = false)
    private String detalle;
}
