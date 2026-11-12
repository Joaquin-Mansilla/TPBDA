package ar.edu.agro.tratamientos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Para qué cultivo y plaga está permitido un agroquímico (T1). */
@Entity
@Table(name = "habilitacion", uniqueConstraints = @UniqueConstraint(columnNames = {"agroquimico_id", "cultivo_id", "plaga_id"}))
@Getter @Setter @NoArgsConstructor
public class Habilitacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "agroquimico_id")
    private Agroquimico agroquimico;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cultivo_id")
    private Cultivo cultivo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "plaga_id")
    private Plaga plaga;
}
