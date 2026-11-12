package ar.edu.agro.tratamientos.model;

import ar.edu.agro.commons.contract.ClaseTextura;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Factor que multiplica la dosis máxima según la textura del suelo (T2). */
@Entity
@Table(name = "ajuste_suelo", uniqueConstraints = @UniqueConstraint(columnNames = {"agroquimico_id", "clase_textura"}))
@Getter @Setter @NoArgsConstructor
public class AjusteSuelo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "agroquimico_id")
    private Agroquimico agroquimico;
    @Enumerated(EnumType.STRING) @Column(name = "clase_textura", nullable = false, length = 10)
    private ClaseTextura claseTextura;
    @Column(name = "factor_dosis_max", nullable = false, precision = 4, scale = 2)
    private BigDecimal factorDosisMax;
}
