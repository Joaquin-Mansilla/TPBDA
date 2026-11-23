package ar.edu.agro.campos.service;

import ar.edu.agro.commons.contract.CultivoResumenResponse;
import ar.edu.agro.commons.contract.MotivoDto;
import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import ar.edu.agro.commons.error.ReglaNegocioException;
import ar.edu.agro.campos.client.TratamientosClient;
import ar.edu.agro.campos.dto.CamposDtos.*;
import ar.edu.agro.campos.model.*;
import ar.edu.agro.campos.repository.CampaniaRepository;
import ar.edu.agro.campos.repository.MonitoreoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Campañas (F04, F05) y monitoreos (F06). Reglas C1 a C5 y C8 (fechas dentro de la campaña); la colaboración con
 * ms-tratamientos es para el cultivo.
 */
@Service
public class CampaniaService {

    private final CampaniaRepository campanias;
    private final MonitoreoRepository monitoreos;
    private final LoteService lotes;
    private final AccesoService acceso;
    private final TratamientosClient tratamientos;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int toleranciaSiembraDias;
    private final BigDecimal umbralAlertaPct;
    private final int anticipoCosechaDias;

    public CampaniaService(CampaniaRepository campanias, MonitoreoRepository monitoreos, LoteService lotes,
                           AccesoService acceso, TratamientosClient tratamientos, TransactionTemplate tx, Clock clock,
                           @Value("${agro.campanias.tolerancia-siembra-dias:45}") int toleranciaSiembraDias,
                           @Value("${agro.monitoreos.umbral-alerta-pct:20}") BigDecimal umbralAlertaPct,
                           @Value("${agro.campanias.anticipo-cosecha-dias:10}") int anticipoCosechaDias) {
        this.campanias = campanias;
        this.monitoreos = monitoreos;
        this.lotes = lotes;
        this.acceso = acceso;
        this.tratamientos = tratamientos;
        this.tx = tx;
        this.clock = clock;
        this.toleranciaSiembraDias = toleranciaSiembraDias;
        this.umbralAlertaPct = umbralAlertaPct;
        this.anticipoCosechaDias = anticipoCosechaDias;
    }

    /** Incidencia desde la que un monitoreo genera una alerta (M1). */
    public BigDecimal umbralAlertaPct() {
        return umbralAlertaPct;
    }

    /**
     * F04. Pide el cultivo a ms-tratamientos (si no responde: 503 y la campaña no se crea) y valida C1 a C4.
     * Se informan todas las reglas incumplidas juntas.
     *
     * Primero se lee y se consulta a ms-tratamientos SIN transacción; recién después se abre una transacción corta
     * para volver a leer las campañas del lote, validar y guardar (no se retiene una conexión mientras se espera).
     */
    public CampaniaResponse planificar(Long loteId, CampaniaRequest r, Usuario u) {
        Lote lote = lotes.buscar(loteId);
        acceso.exigirDueno(lote.getEstablecimiento(), u);
        CultivoResumenResponse cultivo = tratamientos.obtenerCultivo(r.cultivoId());
        LocalDate cosecha = r.fechaSiembra().plusDays(cultivo.diasCiclo());

        // familias de las dos campañas anteriores (C3): también vienen de ms-tratamientos, así que se piden acá
        Map<Long, String> familias = new HashMap<>();
        familias.put(cultivo.id(), cultivo.familia());
        for (Campania anterior : anteriores(vigentes(loteId), r.fechaSiembra())) {
            familias.computeIfAbsent(anterior.getCultivoId(), id -> tratamientos.obtenerCultivo(id).familia());
        }

        return tx.execute(estado -> {
            // se vuelve a leer por si otra campaña se planificó mientras se esperaba a ms-tratamientos
            List<Campania> vigentes = vigentes(loteId);
            List<MotivoDto> motivos = new ArrayList<>();

            // C1: puede convivir con otras campañas si es de otro cultivo y entre todas entran en el lote
            for (String m : ReglasCampania.c1Incumplimientos(r.fechaSiembra(), cosecha, r.cultivoId(),
                    r.superficieSembradaHa(), lote.getSuperficieHa(), ocupaciones(vigentes))) {
                motivos.add(new MotivoDto("C1", m));
            }
            if (!ReglasCampania.c2SuperficieValida(r.superficieSembradaHa(), lote.getSuperficieHa())) {
                motivos.add(new MotivoDto("C2", "La superficie sembrada (" + r.superficieSembradaHa()
                        + " ha) supera la del lote (" + lote.getSuperficieHa() + " ha)"));
            }
            List<Campania> anteriores = anteriores(vigentes, r.fechaSiembra());
            if (anteriores.size() == 2) {
                // una campaña creada en el medio con un cultivo que no se consultó no tiene familia conocida: no cuenta
                List<String> familiasAnteriores = anteriores.stream()
                        .map(c -> familias.get(c.getCultivoId())).filter(Objects::nonNull).toList();
                if (!ReglasCampania.c3RotacionValida(cultivo.familia(), familiasAnteriores)) {
                    motivos.add(new MotivoDto("C3", "Ya hubo dos campañas seguidas de la familia " + cultivo.familia() + " en este lote"));
                }
            }
            if (!ReglasCampania.c4DentroDeVentana(r.fechaSiembra(), cultivo.mesSiembraDesde(), cultivo.mesSiembraHasta())) {
                motivos.add(new MotivoDto("C4", "La fecha de siembra está fuera de la ventana del cultivo (meses "
                        + cultivo.mesSiembraDesde() + " a " + cultivo.mesSiembraHasta() + ")"));
            }
            if (!motivos.isEmpty()) {
                throw ErroresDeRegla.de("La campaña no cumple las reglas de planificación", motivos);
            }

            Campania c = new Campania();
            c.setLote(lote);
            c.setCultivoId(r.cultivoId());
            c.setSuperficieSembradaHa(r.superficieSembradaHa());
            c.setFechaSiembra(r.fechaSiembra());
            c.setCosechaEstimada(cosecha);
            campanias.save(c);
            return aRespuesta(c);
        });
    }

    /** Las campañas perdidas no ocupan el lote ni cuentan para la rotación. */
    private List<Campania> vigentes(Long loteId) {
        return campanias.findByLoteIdOrderByFechaSiembraDesc(loteId).stream()
                .filter(c -> c.getEstado() != EstadoCampania.PERDIDA).toList();
    }

    private static List<Campania> anteriores(List<Campania> vigentes, LocalDate fechaSiembra) {
        return vigentes.stream().filter(c -> c.getFechaSiembra().isBefore(fechaSiembra)).limit(2).toList();
    }

    /** Lo que ocupa cada campaña en el lote (C1). Una sembrada ocupa el lote desde la siembra real. */
    private static List<ReglasCampania.Ocupacion> ocupaciones(List<Campania> campanias) {
        return campanias.stream().map(c -> new ReglasCampania.Ocupacion(siembraEfectiva(c), c.getCosechaEstimada(),
                c.getCultivoId(), c.getSuperficieSembradaHa())).toList();
    }

    @Transactional(readOnly = true)
    public CampaniaResponse obtener(Long id, Usuario u) {
        Campania c = buscar(id);
        acceso.exigirLectura(c.getLote().getEstablecimiento(), u);
        return aRespuesta(c);
    }

    /**
     * F05: registrar la siembra real (PLANIFICADA -> SEMBRADA, C5). Con la fecha real se recalcula la cosecha estimada,
     * para lo que hace falta el ciclo del cultivo: se pide a ms-tratamientos fuera de la transacción.
     */
    public CampaniaResponse registrarSiembra(Long id, SiembraRequest r, Usuario u) {
        Campania leida = buscar(id);
        acceso.exigirDueno(leida.getLote().getEstablecimiento(), u);
        // si la campaña no se puede sembrar no tiene sentido consultar a ms-tratamientos (ni dar 503 en lugar de 422)
        if (!leida.getEstado().puedePasarA(EstadoCampania.SEMBRADA)) {
            throw ErroresDeRegla.de("C5", "No se puede pasar de " + leida.getEstado() + " a " + EstadoCampania.SEMBRADA);
        }
        CultivoResumenResponse cultivo = tratamientos.obtenerCultivo(leida.getCultivoId());
        LocalDate fecha = r.fecha();
        LocalDate cosecha = fecha.plusDays(cultivo.diasCiclo());

        return tx.execute(estado -> {
            Campania c = buscar(id);
            List<MotivoDto> motivos = new ArrayList<>();
            if (!ReglasCampania.c4DentroDeVentana(fecha, cultivo.mesSiembraDesde(), cultivo.mesSiembraHasta())) {
                motivos.add(new MotivoDto("C4", "La fecha de siembra real está fuera de la ventana del cultivo (meses "
                        + cultivo.mesSiembraDesde() + " a " + cultivo.mesSiembraHasta() + ")"));
            }
            if (!ReglasCampania.c4DentroDeTolerancia(c.getFechaSiembra(), fecha, toleranciaSiembraDias)) {
                motivos.add(new MotivoDto("C4", "La siembra real (" + fecha + ") se aparta más de " + toleranciaSiembraDias
                        + " días de la planificada (" + c.getFechaSiembra() + ")"));
            }
            if (!ReglasCampania.c8NoEsFutura(fecha, hoy())) {
                motivos.add(new MotivoDto("C8", "No se puede registrar una siembra con fecha futura"));
            }
            // con la fecha real cambia la cosecha estimada: no puede pisarse con otra campaña del lote
            List<Campania> otras = vigentes(c.getLote().getId()).stream().filter(o -> !o.getId().equals(c.getId())).toList();
            for (String m : ReglasCampania.c1Incumplimientos(fecha, cosecha, c.getCultivoId(),
                    c.getSuperficieSembradaHa(), c.getLote().getSuperficieHa(), ocupaciones(otras))) {
                motivos.add(new MotivoDto("C1", m));
            }
            if (!motivos.isEmpty()) {
                throw ErroresDeRegla.de("La siembra no cumple las reglas de la campaña", motivos);
            }
            c.sembrar(fecha, cosecha);
            campanias.save(c);
            return aRespuesta(c);
        });
    }

    /**
     * F05: registrar la cosecha (SEMBRADA -> COSECHADA, C5). La fecha va de la siembra real a hoy (C8) y no puede ser
     * más de agro.campanias.anticipo-cosecha-dias (10) antes de la cosecha estimada (C9): el cultivo tiene que estar
     * maduro. El mismo día de la estimada o después, sí.
     */
    @Transactional
    public CampaniaResponse registrarCosecha(Long id, CosechaRequest r, Usuario u) {
        Campania c = buscar(id);
        acceso.exigirDueno(c.getLote().getEstablecimiento(), u);
        if (c.getEstado().puedePasarA(EstadoCampania.COSECHADA)) {
            List<MotivoDto> motivos = new ArrayList<>();
            if (r.fecha().isBefore(siembraEfectiva(c))) {
                motivos.add(new MotivoDto("C8", "La cosecha (" + r.fecha() + ") no puede ser anterior a la siembra ("
                        + siembraEfectiva(c) + ")"));
            }
            if (!ReglasCampania.c8NoEsFutura(r.fecha(), hoy())) {
                motivos.add(new MotivoDto("C8", "No se puede registrar una cosecha con fecha futura"));
            }
            if (!ReglasCampania.c9CosechaATiempo(r.fecha(), c.getCosechaEstimada(), anticipoCosechaDias)) {
                motivos.add(new MotivoDto("C9", "La cosecha (" + r.fecha() + ") es demasiado temprana: la estimada es el "
                        + c.getCosechaEstimada() + " y se puede cosechar desde " + anticipoCosechaDias + " días antes ("
                        + c.getCosechaEstimada().minusDays(anticipoCosechaDias) + ")"));
            }
            if (!motivos.isEmpty()) {
                throw ErroresDeRegla.de("La cosecha no cumple las reglas de la campaña", motivos);
            }
        }
        c.cosechar(r.fecha(), r.kgCosechados());   // C5 si no estaba SEMBRADA
        campanias.save(c);
        return aRespuesta(c);
    }

    /**
     * F05: marcar la campaña como perdida con motivo (PLANIFICADA o SEMBRADA -> PERDIDA, C5).
     * La fecha no puede ser futura y, si ya estaba sembrada, tampoco anterior a la siembra real (C8).
     */
    @Transactional
    public CampaniaResponse registrarPerdida(Long id, PerdidaRequest r, Usuario u) {
        Campania c = buscar(id);
        acceso.exigirDueno(c.getLote().getEstablecimiento(), u);
        if (c.getEstado().puedePasarA(EstadoCampania.PERDIDA)) {
            List<MotivoDto> motivos = new ArrayList<>();
            if (!ReglasCampania.c8NoEsFutura(r.fecha(), hoy())) {
                motivos.add(new MotivoDto("C8", "No se puede registrar una pérdida con fecha futura"));
            }
            if (c.getEstado() == EstadoCampania.SEMBRADA && r.fecha().isBefore(siembraEfectiva(c))) {
                motivos.add(new MotivoDto("C8", "La pérdida (" + r.fecha() + ") no puede ser anterior a la siembra ("
                        + siembraEfectiva(c) + ")"));
            }
            if (!motivos.isEmpty()) {
                throw ErroresDeRegla.de("La pérdida no cumple las reglas de la campaña", motivos);
            }
        }
        c.perder(r.fecha(), r.motivo());   // C5 si ya estaba cosechada o perdida
        campanias.save(c);
        return aRespuesta(c);
    }

    /**
     * F06: solo en una campaña sembrada (C5), con fecha entre la siembra real y hoy (C8). Sin fecha, es hoy.
     * Lo registran el productor dueño o el operario asignado.
     */
    @Transactional
    public MonitoreoResponse registrarMonitoreo(Long campaniaId, MonitoreoRequest r, Usuario u) {
        Campania c = buscar(campaniaId);
        acceso.exigirDuenoOAsignado(c.getLote().getEstablecimiento(), u);
        if (c.getEstado() != EstadoCampania.SEMBRADA) {
            throw ErroresDeRegla.de("C5", "Solo se registran monitoreos en campañas sembradas");
        }
        LocalDate fecha = r.fecha() != null ? r.fecha() : hoy();
        if (!ReglasCampania.c8DentroDe(fecha, siembraEfectiva(c), hoy())) {
            throw ErroresDeRegla.de("C8", "La fecha del monitoreo tiene que estar entre la siembra (" + siembraEfectiva(c)
                    + ") y hoy (" + hoy() + ")");
        }
        Monitoreo m = new Monitoreo();
        m.setCampania(c);
        m.setPlagaId(r.plagaId());
        m.setIncidenciaPct(r.incidenciaPct());
        m.setFecha(fecha);
        m.setRegistradoPorSub(u.sub());
        m.setRegistradoPorNombre(u.nombre());
        monitoreos.save(m);
        // M1: si la incidencia llega al umbral, el monitoreo queda como alerta hasta que se aplique contra esa plaga
        boolean alerta = esAlerta(m.getIncidenciaPct());
        String mensaje = alerta
                ? "Alerta: la incidencia (" + m.getIncidenciaPct().stripTrailingZeros().toPlainString() + " %) llega al umbral ("
                  + umbralAlertaPct.stripTrailingZeros().toPlainString() + " %). Se recomienda registrar una aplicación contra la plaga " + m.getPlagaId()
                : "Incidencia por debajo del umbral (" + umbralAlertaPct.stripTrailingZeros().toPlainString() + " %): no hace falta aplicar";
        return new MonitoreoResponse(m.getId(), c.getId(), m.getPlagaId(), m.getIncidenciaPct(), m.getFecha(),
                m.getRegistradoPorSub(), m.getRegistradoPorNombre(), alerta, mensaje);
    }

    /** M1: la incidencia llega (o supera) el umbral configurado. */
    public boolean esAlerta(BigDecimal incidenciaPct) {
        return incidenciaPct.compareTo(umbralAlertaPct) >= 0;
    }

    public Campania buscar(Long id) {
        return campanias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe la campaña " + id));
    }

    /**
     * Fecha desde la que la campaña está en el lote: la siembra real. Las campañas sembradas antes de la T16 no la
     * tienen guardada; para ellas vale la planificada.
     */
    public static LocalDate siembraEfectiva(Campania c) {
        return c.getFechaSiembraReal() != null ? c.getFechaSiembraReal() : c.getFechaSiembra();
    }

    private LocalDate hoy() {
        return LocalDate.now(clock);
    }

    private CampaniaResponse aRespuesta(Campania c) {
        return new CampaniaResponse(c.getId(), c.getLote().getId(), c.getCultivoId(), c.getSuperficieSembradaHa(),
                c.getFechaSiembra(), c.getCosechaEstimada(), c.getEstado(), c.getMotivoPerdida(),
                c.getFechaSiembraReal(), c.getFechaCosecha(), c.getFechaPerdida(), c.getKgCosechados(),
                c.rendimientoKgHa());
    }
}
