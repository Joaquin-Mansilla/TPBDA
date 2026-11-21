package ar.edu.agro.campos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "establecimiento")
@Getter @Setter @NoArgsConstructor
public class Establecimiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String nombre;
    /** sub del token de Keycloak del dueño */
    @Column(name = "productor_sub", nullable = false, length = 36)
    private String productorSub;
    /** operarios asignados (regla C6) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "establecimiento_operario", joinColumns = @JoinColumn(name = "establecimiento_id"))
    @Column(name = "operario_sub", length = 36)
    private Set<String> operarios = new HashSet<>();
    @OneToMany(mappedBy = "establecimiento")
    private List<Lote> lotes = new ArrayList<>();

    // Límites del campo (T16): un rectángulo de coordenadas. Columnas nullables para no romper bases ya creadas.
    @Column(name = "latitud_min", precision = 9, scale = 6)
    private BigDecimal latitudMin;
    @Column(name = "latitud_max", precision = 9, scale = 6)
    private BigDecimal latitudMax;
    @Column(name = "longitud_min", precision = 9, scale = 6)
    private BigDecimal longitudMin;
    @Column(name = "longitud_max", precision = 9, scale = 6)
    private BigDecimal longitudMax;
    /** hectáreas del rectángulo, calculadas con GeometriaCampo al crear el establecimiento */
    @Column(name = "superficie_ha", precision = 10, scale = 2)
    private BigDecimal superficieHa;

    /** El rectángulo del establecimiento, o null si se creó antes de la T16 y no tiene límites. */
    public GeometriaCampo.Rectangulo rectangulo() {
        if (latitudMin == null || latitudMax == null || longitudMin == null || longitudMax == null) {
            return null;
        }
        return new GeometriaCampo.Rectangulo(latitudMin, latitudMax, longitudMin, longitudMax);
    }
}
