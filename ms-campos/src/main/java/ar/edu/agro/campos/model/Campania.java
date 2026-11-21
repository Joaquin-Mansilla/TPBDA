package ar.edu.agro.campos.model;

import ar.edu.agro.campos.service.ErroresDeRegla;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "campania")
@Getter @Setter @NoArgsConstructor
public class Campania {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "lote_id")
    private Lote lote;
    /** referencia lógica a tratamientos.cultivo */
    @Column(name = "cultivo_id", nullable = false)
    private Long cultivoId;
    @Column(name = "superficie_sembrada_ha", nullable = false, precision = 10, scale = 2)
    private BigDecimal superficieSembradaHa;
    /** fecha de siembra PLANIFICADA (la real queda en fechaSiembraReal) */
    @Column(name = "fecha_siembra", nullable = false)
    private LocalDate fechaSiembra;
    @Column(name = "cosecha_estimada", nullable = false)
    private LocalDate cosechaEstimada;
    /** el estado solo cambia con sembrar, cosechar y perder (C5) */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private EstadoCampania estado = EstadoCampania.PLANIFICADA;
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_siembra_real")
    private LocalDate fechaSiembraReal;
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_cosecha")
    private LocalDate fechaCosecha;
    @Setter(AccessLevel.NONE)
    @Column(name = "fecha_perdida")
    private LocalDate fechaPerdida;
    @Setter(AccessLevel.NONE)
    @Column(name = "motivo_perdida")
    private String motivoPerdida;
    @Version @Column(nullable = false)
    private Long version;

    /** Registra la siembra real; con ella cambia la cosecha estimada. Las reglas de fechas las valida el servicio. */
    public void sembrar(LocalDate fechaReal, LocalDate cosechaEstimadaNueva) {
        exigirTransicion(EstadoCampania.SEMBRADA);
        this.fechaSiembraReal = fechaReal;
        this.cosechaEstimada = cosechaEstimadaNueva;
        this.estado = EstadoCampania.SEMBRADA;
    }

    public void cosechar(LocalDate fecha) {
        exigirTransicion(EstadoCampania.COSECHADA);
        this.fechaCosecha = fecha;
        this.estado = EstadoCampania.COSECHADA;
    }

    /** Perder exige motivo (C5). */
    public void perder(LocalDate fecha, String motivo) {
        exigirTransicion(EstadoCampania.PERDIDA);
        if (motivo == null || motivo.isBlank()) {
            throw ErroresDeRegla.de("C5", "Para marcar la campaña como perdida hay que indicar el motivo");
        }
        this.fechaPerdida = fecha;
        this.motivoPerdida = motivo.trim();
        this.estado = EstadoCampania.PERDIDA;
    }

    private void exigirTransicion(EstadoCampania nuevo) {
        if (!estado.puedePasarA(nuevo)) {
            throw ErroresDeRegla.de("C5", "No se puede pasar de " + estado + " a " + nuevo);
        }
    }
}
