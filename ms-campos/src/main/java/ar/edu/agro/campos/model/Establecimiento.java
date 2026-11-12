package ar.edu.agro.campos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
