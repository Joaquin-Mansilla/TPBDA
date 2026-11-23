package ar.edu.agro.campos.service;

import ar.edu.agro.campos.client.TratamientosClient;
import ar.edu.agro.campos.dto.CamposDtos.EstadoRiegoResponse;
import ar.edu.agro.campos.dto.CamposDtos.LluviaRequest;
import ar.edu.agro.campos.dto.CamposDtos.LluviaResponse;
import ar.edu.agro.campos.dto.CamposDtos.RiegoRequest;
import ar.edu.agro.campos.dto.CamposDtos.RiegoResponse;
import ar.edu.agro.campos.model.Campania;
import ar.edu.agro.campos.model.EstadoCampania;
import ar.edu.agro.campos.model.EstadoRiego;
import ar.edu.agro.campos.model.Lluvia;
import ar.edu.agro.campos.model.Lote;
import ar.edu.agro.campos.model.OrigenAgua;
import ar.edu.agro.campos.model.Riego;
import ar.edu.agro.campos.repository.LluviaRepository;
import ar.edu.agro.campos.repository.RiegoRepository;
import ar.edu.agro.commons.contract.CultivoResumenResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Riego (R1): registrar los riegos de una campaña y las lluvias del lote, y calcular si toca regar.
 *
 * El estado se calcula al consultarlo (no se guarda) con la frecuencia del cultivo (ms-tratamientos), los riegos de
 * la campaña y las lluvias que se cargaron a mano en el lote. Si ms-tratamientos no responde, 503 (sin la frecuencia
 * del cultivo no hay cálculo posible). Un día sin lluvia cargada cuenta como 0 mm: nunca se inventa la lluvia.
 *
 * consultarEstado no es @Transactional a propósito: no se retiene una conexión mientras se espera a otro servicio.
 */
@Service
public class RiegoService {

    private final RiegoRepository riegos;
    private final LluviaRepository lluvias;
    private final CampaniaService campanias;
    private final LoteService lotes;
    private final AccesoService acceso;
    private final TratamientosClient tratamientos;
    private final Clock clock;
    private final int toleranciaDias;

    public RiegoService(RiegoRepository riegos, LluviaRepository lluvias, CampaniaService campanias, LoteService lotes,
                        AccesoService acceso, TratamientosClient tratamientos, Clock clock,
                        @Value("${agro.riego.tolerancia-dias:2}") int toleranciaDias) {
        this.riegos = riegos;
        this.lluvias = lluvias;
        this.campanias = campanias;
        this.lotes = lotes;
        this.acceso = acceso;
        this.tratamientos = tratamientos;
        this.clock = clock;
        this.toleranciaDias = toleranciaDias;
    }

    /**
     * Carga a mano la lluvia de un día en el lote (lo que marcó el pluviómetro). Sin fecha, es hoy; no puede ser
     * futura (C8). 0 mm deja registrado que no llovió. Si se carga dos veces el mismo día, se suman.
     * La cargan el productor dueño o el operario asignado.
     */
    @Transactional
    public LluviaResponse registrarLluvia(Long loteId, LluviaRequest r, Usuario u) {
        Lote lote = lotes.buscar(loteId);
        acceso.exigirDuenoOAsignado(lote.getEstablecimiento(), u);
        LocalDate fecha = r.fecha() != null ? r.fecha() : hoy();
        if (!ReglasCampania.c8NoEsFutura(fecha, hoy())) {
            throw ErroresDeRegla.de("C8", "No se puede cargar la lluvia de una fecha futura");
        }
        Lluvia l = new Lluvia();
        l.setLote(lote);
        l.setFecha(fecha);
        l.setMm(r.mm());
        l.setRegistradoPorSub(u.sub());
        l.setRegistradoPorNombre(u.nombre());
        lluvias.save(l);
        return aRespuesta(l);
    }

    @Transactional(readOnly = true)
    public List<LluviaResponse> listarLluvias(Long loteId, Usuario u) {
        Lote lote = lotes.buscar(loteId);
        acceso.exigirLectura(lote.getEstablecimiento(), u);
        return lluvias.findByLoteIdOrderByFechaAscIdAsc(loteId).stream().map(this::aRespuesta).toList();
    }

    private LluviaResponse aRespuesta(Lluvia l) {
        return new LluviaResponse(l.getId(), l.getLote().getId(), l.getFecha(), l.getMm(), l.getRegistradoPorSub(),
                l.getRegistradoPorNombre());
    }

    /**
     * Solo en una campaña sembrada (C5), con fecha entre la siembra real y hoy (C8). Sin fecha, es hoy.
     * Lo registran el productor dueño o el operario asignado.
     */
    @Transactional
    public RiegoResponse registrar(Long campaniaId, RiegoRequest r, Usuario u) {
        Campania c = campanias.buscar(campaniaId);
        acceso.exigirDuenoOAsignado(c.getLote().getEstablecimiento(), u);
        if (c.getEstado() != EstadoCampania.SEMBRADA) {
            throw ErroresDeRegla.de("C5", "Solo se registran riegos en campañas sembradas");
        }
        LocalDate fecha = r.fecha() != null ? r.fecha() : hoy();
        LocalDate siembra = CampaniaService.siembraEfectiva(c);
        if (!ReglasCampania.c8DentroDe(fecha, siembra, hoy())) {
            throw ErroresDeRegla.de("C8", "La fecha del riego tiene que estar entre la siembra (" + siembra
                    + ") y hoy (" + hoy() + ")");
        }
        Riego riego = new Riego();
        riego.setCampania(c);
        riego.setFecha(fecha);
        riego.setLaminaMm(r.laminaMm());
        riego.setRegistradoPorSub(u.sub());
        riego.setRegistradoPorNombre(u.nombre());
        riegos.save(riego);
        return new RiegoResponse(riego.getId(), c.getId(), riego.getFecha(), riego.getLaminaMm(), riego.getRegistradoPorSub(),
                riego.getRegistradoPorNombre());
    }

    public List<RiegoResponse> listar(Long campaniaId, Usuario u) {
        Campania c = campanias.buscar(campaniaId);
        acceso.exigirLectura(c.getLote().getEstablecimiento(), u);
        return riegos.findByCampaniaIdOrderByFechaAsc(campaniaId).stream()
                .map(x -> new RiegoResponse(x.getId(), campaniaId, x.getFecha(), x.getLaminaMm(), x.getRegistradoPorSub(),
                        x.getRegistradoPorNombre()))
                .toList();
    }

    /** R1: ¿toca regar? Lo puede consultar cualquiera que pueda leer la campaña (C6). */
    public EstadoRiegoResponse consultarEstado(Long campaniaId, Usuario u) {
        Campania c = campanias.buscar(campaniaId);
        acceso.exigirLectura(c.getLote().getEstablecimiento(), u);
        List<Riego> registrados = riegos.findByCampaniaIdOrderByFechaAsc(campaniaId);
        BigDecimal totalRegado = registrados.stream().map(Riego::getLaminaMm).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (c.getEstado() != EstadoCampania.SEMBRADA) {
            return sinCalculo(c, null, EstadoRiego.NO_SEMBRADA,
                    "La campaña está " + c.getEstado() + ": solo se riegan las campañas sembradas", registrados, totalRegado);
        }
        CultivoResumenResponse cultivo = tratamientos.obtenerCultivo(c.getCultivoId());
        if (!cultivo.requiereRiego()) {
            return sinCalculo(c, cultivo, EstadoRiego.NO_REQUIERE,
                    "El cultivo " + cultivo.nombre() + " se hace a secano: no lleva riego", registrados, totalRegado);
        }

        Map<LocalDate, BigDecimal> riegosPorDia = new HashMap<>();
        for (Riego x : registrados) {
            riegosPorDia.merge(x.getFecha(), x.getLaminaMm(), BigDecimal::add);
        }
        // la lluvia es del lote: la que se cargó a mano cuenta para todas sus campañas
        Map<LocalDate, BigDecimal> lluviaPorDia = new HashMap<>();
        for (Lluvia l : lluvias.findByLoteIdOrderByFechaAscIdAsc(c.getLote().getId())) {
            lluviaPorDia.merge(l.getFecha(), l.getMm(), BigDecimal::add);
        }

        ReglasRiego.Resultado res = ReglasRiego.calcular(CampaniaService.siembraEfectiva(c), riegosPorDia, lluviaPorDia,
                cultivo.diasEntreRiegos(), cultivo.mmPorRiego(), toleranciaDias, hoy());

        return new EstadoRiegoResponse(c.getId(), cultivo.id(), cultivo.nombre(), res.estado(),
                mensaje(res, cultivo), cultivo.diasEntreRiegos(), cultivo.mmPorRiego(),
                res.ultimaAgua(), res.origen(), res.diasSinAgua(), res.proximoRiego(), res.mmDesdeUltimaAgua(),
                cultivo.mmPorRiego().subtract(res.mmDesdeUltimaAgua()),
                res.lluviaTotalMm(), registrados.size(), totalRegado);
    }

    private EstadoRiegoResponse sinCalculo(Campania c, CultivoResumenResponse cultivo, EstadoRiego estado, String mensaje,
                                           List<Riego> registrados, BigDecimal totalRegado) {
        return new EstadoRiegoResponse(c.getId(), c.getCultivoId(), cultivo == null ? null : cultivo.nombre(), estado,
                mensaje, null, null, null, null, null, null, null, null, null, registrados.size(), totalRegado);
    }

    /**
     * Explica el estado en palabras. Al sembrar, el cultivo necesita su primer riego completo (riego de asiento):
     * hasta que lo reciba, entre riego y lluvia, no está al día.
     */
    String mensaje(ReglasRiego.Resultado res, CultivoResumenResponse cultivo) {
        String mm = mm(cultivo.mmPorRiego());
        String cada = "el cultivo pide " + mm + " mm cada " + cultivo.diasEntreRiegos() + " días";
        StringBuilder texto = new StringBuilder();
        if (res.origen() == OrigenAgua.SIEMBRA) {
            // nunca recibió agua completa: falta el riego de asiento
            texto.append(res.estado() == EstadoRiego.ATRASADO ? "Riego atrasado: " : "Toca regar: ")
                    .append("sembrada el ").append(res.ultimaAgua())
                    .append(" y todavía no recibió el primer riego (").append(mm).append(" mm)");
            if (res.estado() == EstadoRiego.ATRASADO) {
                texto.append(". Pasaron ").append(res.diasSinAgua()).append(" días y la tolerancia es de ")
                        .append(toleranciaDias);
            }
        } else {
            String desde = res.origen() == OrigenAgua.RIEGO
                    ? "el último riego completo fue el " + res.ultimaAgua()
                    : "el " + res.ultimaAgua() + " la lluvia alcanzó los " + mm + " mm y contó como riego";
            switch (res.estado()) {
                case AL_DIA -> texto.append("Al día: ").append(desde).append(". Próximo riego: ").append(res.proximoRiego());
                case TOCA_REGAR -> texto.append("Toca regar: ").append(desde).append(" (hace ").append(res.diasSinAgua())
                        .append(" días; ").append(cada).append(")");
                default -> texto.append("Riego atrasado: ").append(desde).append(" (hace ").append(res.diasSinAgua())
                        .append(" días; ").append(cada).append(" y la tolerancia es de ").append(toleranciaDias).append(" días)");
            }
        }
        // lo que juntó después de la última agua completa todavía cuenta: se dice cuánto le falta
        if (res.mmDesdeUltimaAgua().signum() > 0) {
            texto.append(". ").append(res.origen() == OrigenAgua.SIEMBRA ? "Desde la siembra" : "Desde entonces")
                    .append(" juntó ").append(mm(res.mmDesdeUltimaAgua()))
                    .append(" mm entre riego y lluvia: le faltan ")
                    .append(mm(cultivo.mmPorRiego().subtract(res.mmDesdeUltimaAgua())))
                    .append(" mm para completar un riego");
        }
        return texto.toString();
    }

    private static String mm(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private LocalDate hoy() {
        return LocalDate.now(clock);
    }
}
