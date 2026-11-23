package ar.edu.agro.tratamientos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

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
    /** Cada cuántos días necesita agua. null (junto con mmPorRiego) = se hace a secano. */
    @Column(name = "dias_entre_riegos")
    private Integer diasEntreRiegos;
    /** Lámina de un riego en mm; una lluvia que llega a este valor cuenta como un riego. */
    @Column(name = "mm_por_riego", precision = 6, scale = 1)
    private BigDecimal mmPorRiego;
    @Column(nullable = false)
    private boolean activo = true;
}
