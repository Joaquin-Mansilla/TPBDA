package ar.edu.agro.tratamientos.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "agroquimico")
@Getter @Setter @NoArgsConstructor
public class Agroquimico {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String nombre;
    @Column(name = "principio_activo", nullable = false, length = 80)
    private String principioActivo;
    @Column(name = "dosis_min_ha", nullable = false, precision = 10, scale = 3)
    private BigDecimal dosisMinHa;
    @Column(name = "dosis_max_ha", nullable = false, precision = 10, scale = 3)
    private BigDecimal dosisMaxHa;
    @Column(nullable = false, length = 10)
    private String unidad;
    @Column(name = "contenido_envase", nullable = false, precision = 10, scale = 2)
    private BigDecimal contenidoEnvase;
    @Column(name = "precio_envase", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioEnvase;
    @Column(name = "dias_carencia", nullable = false)
    private Integer diasCarencia;
    @Column(name = "intervalo_min_dias", nullable = false)
    private Integer intervaloMinDias;
    @Column(nullable = false)
    private boolean activo = true;
}
