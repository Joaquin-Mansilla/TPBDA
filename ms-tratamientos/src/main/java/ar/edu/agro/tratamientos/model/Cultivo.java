package ar.edu.agro.tratamientos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cultivo")
@Getter @Setter @NoArgsConstructor
public class Cultivo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String nombre;
    @Column(nullable = false, length = 60)
    private String familia;
    @Column(name = "dias_ciclo", nullable = false)
    private Integer diasCiclo;
    @Column(name = "mes_siembra_desde", nullable = false)
    private Integer mesSiembraDesde;
    @Column(name = "mes_siembra_hasta", nullable = false)
    private Integer mesSiembraHasta;
    @Column(nullable = false)
    private boolean activo = true;
}
