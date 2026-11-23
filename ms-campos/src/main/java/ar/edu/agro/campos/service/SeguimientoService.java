package ar.edu.agro.campos.service;

import ar.edu.agro.campos.client.ApiExternaException;
import ar.edu.agro.campos.client.TratamientosClient;
import ar.edu.agro.campos.dto.CamposDtos.AlertaResponse;
import ar.edu.agro.campos.dto.CamposDtos.CampaniaHistorial;
import ar.edu.agro.campos.dto.CamposDtos.HistorialLoteResponse;
import ar.edu.agro.campos.dto.CamposDtos.RendimientoCultivo;
import ar.edu.agro.campos.model.Aplicacion;
import ar.edu.agro.campos.model.Campania;
import ar.edu.agro.campos.model.EstadoAplicacion;
import ar.edu.agro.campos.model.EstadoCampania;
import ar.edu.agro.campos.model.Lote;
import ar.edu.agro.campos.model.Monitoreo;
import ar.edu.agro.campos.model.Riego;
import ar.edu.agro.campos.repository.AplicacionRepository;
import ar.edu.agro.campos.repository.CampaniaRepository;
import ar.edu.agro.campos.repository.MonitoreoRepository;
import ar.edu.agro.campos.repository.RiegoRepository;
import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Seguimiento de lo que pasa en el campo:
 * - Alertas (M1): monitoreos que llegaron al umbral de incidencia y si ya se aplicó contra esa plaga.
 * - Historial del lote: todas sus campañas con lo que se hizo en cada una y el rendimiento por cultivo.
 *
 * Los nombres de los cultivos vienen de ms-tratamientos; si no responde, el historial sale igual con los nombres en
 * null (es una consulta, no tiene sentido negarla por un dato descriptivo).
 */
@Service
public class SeguimientoService {

    private static final Logger log = LoggerFactory.getLogger(SeguimientoService.class);
    /** Aplicaciones que de verdad se hicieron: las que cierran una alerta. */
    private static final Set<EstadoAplicacion> HECHAS = Set.of(EstadoAplicacion.REGISTRADA, EstadoAplicacion.AUTORIZADA_MANUALMENTE);

    private final CampaniaRepository campanias;
    private final MonitoreoRepository monitoreos;
    private final AplicacionRepository aplicaciones;
    private final RiegoRepository riegos;
    private final CampaniaService campaniaService;
    private final LoteService lotes;
    private final AccesoService acceso;
    private final TratamientosClient tratamientos;

    public SeguimientoService(CampaniaRepository campanias, MonitoreoRepository monitoreos,
                              AplicacionRepository aplicaciones, RiegoRepository riegos, CampaniaService campaniaService,
                              LoteService lotes, AccesoService acceso, TratamientosClient tratamientos) {
        this.campanias = campanias;
        this.monitoreos = monitoreos;
        this.aplicaciones = aplicaciones;
        this.riegos = riegos;
        this.campaniaService = campaniaService;
        this.lotes = lotes;
        this.acceso = acceso;
        this.tratamientos = tratamientos;
    }

    /** M1: alertas de la campaña, las pendientes primero. soloPendientes=true deja afuera las ya atendidas. */
    public List<AlertaResponse> alertas(Long campaniaId, boolean soloPendientes, Usuario u) {
        Campania c = campaniaService.buscar(campaniaId);
        acceso.exigirLectura(c.getLote().getEstablecimiento(), u);
        List<AlertaResponse> alertas = calcularAlertas(campaniaId);
        return alertas.stream()
                .filter(a -> !soloPendientes || !a.atendida())
                .sorted(Comparator.comparing(AlertaResponse::atendida).thenComparing(AlertaResponse::fecha))
                .toList();
    }

    private List<AlertaResponse> calcularAlertas(Long campaniaId) {
        List<Aplicacion> hechas = aplicaciones.findByCampaniaIdOrderByFechaAsc(campaniaId).stream()
                .filter(a -> HECHAS.contains(a.getEstado())).toList();
        List<AlertaResponse> alertas = new ArrayList<>();
        for (Monitoreo m : monitoreos.findByCampaniaIdOrderByFechaAscIdAsc(campaniaId)) {
            if (!campaniaService.esAlerta(m.getIncidenciaPct())) {
                continue;
            }
            // la primera aplicación hecha contra esa plaga desde el día del monitoreo la atiende
            Optional<Aplicacion> atiende = hechas.stream()
                    .filter(a -> a.getPlagaId().equals(m.getPlagaId()) && !a.getFecha().isBefore(m.getFecha()))
                    .findFirst();
            String mensaje = atiende
                    .map(a -> "Atendida con la aplicación " + a.getId() + " del " + a.getFecha())
                    .orElse("Pendiente: incidencia de " + m.getIncidenciaPct().stripTrailingZeros().toPlainString()
                            + " % (umbral " + campaniaService.umbralAlertaPct().stripTrailingZeros().toPlainString()
                            + " %). Falta registrar una aplicación contra la plaga " + m.getPlagaId());
            alertas.add(new AlertaResponse(m.getId(), m.getPlagaId(), m.getIncidenciaPct(), m.getFecha(),
                    atiende.isPresent(), atiende.map(Aplicacion::getId).orElse(null), mensaje));
        }
        return alertas;
    }

    /** Historial del lote: campañas (la más reciente primero) y rendimiento promedio por cultivo. */
    public HistorialLoteResponse historial(Long loteId, Usuario u) {
        Lote lote = lotes.buscar(loteId);
        acceso.exigirLectura(lote.getEstablecimiento(), u);
        List<Campania> delLote = campanias.findByLoteIdOrderByFechaSiembraDesc(loteId);
        Map<Long, String> nombres = nombresDeCultivos(delLote);

        List<CampaniaHistorial> items = new ArrayList<>();
        for (Campania c : delLote) {
            List<Riego> regados = riegos.findByCampaniaIdOrderByFechaAsc(c.getId());
            int pendientes = (int) calcularAlertas(c.getId()).stream().filter(a -> !a.atendida()).count();
            int cantAplicaciones = aplicaciones.findByCampaniaIdOrderByFechaAsc(c.getId()).size();
            int cantMonitoreos = monitoreos.findByCampaniaIdOrderByFechaAscIdAsc(c.getId()).size();
            items.add(new CampaniaHistorial(c.getId(), c.getCultivoId(), nombres.get(c.getCultivoId()), c.getEstado(),
                    c.getSuperficieSembradaHa(), CampaniaService.siembraEfectiva(c), c.getCosechaEstimada(),
                    c.getFechaCosecha(), c.getKgCosechados(), c.rendimientoKgHa(), cantMonitoreos, pendientes,
                    cantAplicaciones, regados.size(),
                    regados.stream().map(Riego::getLaminaMm).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        return new HistorialLoteResponse(lote.getId(), lote.getNombre(), lote.getSuperficieHa(), items,
                rendimientos(delLote, nombres));
    }

    /** Promedio ponderado: kg totales / ha totales de las campañas cosechadas con kg informados. */
    static List<RendimientoCultivo> rendimientos(List<Campania> delLote, Map<Long, String> nombres) {
        Map<Long, List<Campania>> porCultivo = new LinkedHashMap<>();
        for (Campania c : delLote) {
            if (c.getEstado() == EstadoCampania.COSECHADA && c.getKgCosechados() != null) {
                porCultivo.computeIfAbsent(c.getCultivoId(), k -> new ArrayList<>()).add(c);
            }
        }
        List<RendimientoCultivo> resultado = new ArrayList<>();
        porCultivo.forEach((cultivoId, lista) -> {
            BigDecimal ha = lista.stream().map(Campania::getSuperficieSembradaHa).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal kg = lista.stream().map(Campania::getKgCosechados).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal promedio = ha.signum() > 0 ? kg.divide(ha, 1, RoundingMode.HALF_UP) : null;
            resultado.add(new RendimientoCultivo(cultivoId, nombres.get(cultivoId), lista.size(), ha, kg, promedio));
        });
        return resultado;
    }

    private Map<Long, String> nombresDeCultivos(List<Campania> delLote) {
        Map<Long, String> nombres = new HashMap<>();
        for (Long id : delLote.stream().map(Campania::getCultivoId).distinct().toList()) {
            try {
                nombres.put(id, tratamientos.obtenerCultivo(id).nombre());
            } catch (ApiExternaException | RecursoNoEncontradoException e) {
                log.warn("Historial sin el nombre del cultivo {}: {}", id, e.getMessage());
            }
        }
        return nombres;
    }
}
