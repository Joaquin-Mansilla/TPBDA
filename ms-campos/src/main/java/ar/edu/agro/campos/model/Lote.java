package ar.edu.agro.campos.model;

import ar.edu.agro.commons.contract.ClaseTextura;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "lote")
@Getter @Setter @NoArgsConstructor
public class Lote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "establecimiento_id")
    private Establecimiento establecimiento;
    @Column(nullable = false, length = 100)
    private String nombre;
    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitud;
    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitud;
    @Column(name = "superficie_ha", nullable = false, precision = 10, scale = 2)
    private BigDecimal superficieHa;
    @Column(length = 80)
    private String provincia;
    @Column(length = 80)
    private String departamento;
    @Enumerated(EnumType.STRING) @Column(name = "estado_suelo", nullable = false, length = 10)
    private EstadoSuelo estadoSuelo = EstadoSuelo.SIN_DATOS;
    @Enumerated(EnumType.STRING) @Column(name = "clase_textura", length = 10)
    private ClaseTextura claseTextura;
    @Column(name = "arcilla_pct", precision = 5, scale = 2)
    private BigDecimal arcillaPct;
    @Column(name = "arena_pct", precision = 5, scale = 2)
    private BigDecimal arenaPct;
    @Column(name = "limo_pct", precision = 5, scale = 2)
    private BigDecimal limoPct;
    @Column(precision = 4, scale = 2)
    private BigDecimal ph;
    @Column(name = "carbono_organico", precision = 6, scale = 2)
    private BigDecimal carbonoOrganico;
}
