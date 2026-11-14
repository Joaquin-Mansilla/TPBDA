package ar.edu.agro.tratamientos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Provincia (y opcionalmente departamento) donde el producto no se puede aplicar (T5). */
@Entity
@Table(name = "restriccion_territorial")
@Getter @Setter @NoArgsConstructor
public class RestriccionTerritorial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "agroquimico_id")
    private Agroquimico agroquimico;
    @Column(nullable = false, length = 80)
    private String provincia;
    /** vacío = toda la provincia */
    @Column(length = 80)
    private String departamento;
}
