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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Campañas (F04, F05) y monitoreos (F06). Reglas C1 a C5; la colaboración con ms-tratamientos es para el cultivo. */
@Service
public class CampaniaService {

    private final CampaniaRepository campanias;
    private final MonitoreoRepository monitoreos;
    private final LoteService lotes;
    private final AccesoService acceso;
    private final TratamientosClient tratamientos;
    private final TransactionTemplate tx;

    public CampaniaService(CampaniaRepository campanias, MonitoreoRepository monitoreos, LoteService lotes,
                           AccesoService acceso, TratamientosClient tratamientos, TransactionTemplate tx) {
        this.campanias = campanias;
        this.monitoreos = monitoreos;
        this.lotes = lotes;
        this.acceso = acceso;
        this.tratamientos = tratamientos;
        this.tx = tx;
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

            if (ReglasCampania.c1Superpone(r.fechaSiembra(), cosecha, rangos(vigentes))) {
                motivos.add(new MotivoDto("C1", "Las fechas se superponen con otra campaña del lote"));
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

    private static List<ReglasCampania.Rango> rangos(List<Campania> campanias) {
        return campanias.stream().map(c -> new ReglasCampania.Rango(c.getFechaSiembra(), c.getCosechaEstimada())).toList();
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
        LocalDate cosecha = r.fecha().plusDays(cultivo.diasCiclo());

        return tx.execute(estado -> {
            Campania c = buscar(id);
            c.sembrar(r.fecha(), cosecha);
            campanias.save(c);
            return aRespuesta(c);
        });
    }

    /** F05: registrar la cosecha (SEMBRADA -> COSECHADA, C5). */
    @Transactional
    public CampaniaResponse registrarCosecha(Long id, CosechaRequest r, Usuario u) {
        Campania c = buscar(id);
        acceso.exigirDueno(c.getLote().getEstablecimiento(), u);
        c.cosechar(r.fecha());
        campanias.save(c);
        return aRespuesta(c);
    }

    /** F05: marcar la campaña como perdida con motivo (PLANIFICADA o SEMBRADA -> PERDIDA, C5). */
    @Transactional
    public CampaniaResponse registrarPerdida(Long id, PerdidaRequest r, Usuario u) {
        Campania c = buscar(id);
        acceso.exigirDueno(c.getLote().getEstablecimiento(), u);
        c.perder(r.fecha(), r.motivo());
        campanias.save(c);
        return aRespuesta(c);
    }

    /** F06: solo en una campaña sembrada (C5). Lo registran el productor dueño o el operario asignado. */
    @Transactional
    public MonitoreoResponse registrarMonitoreo(Long campaniaId, MonitoreoRequest r, Usuario u) {
        Campania c = buscar(campaniaId);
        acceso.exigirDuenoOAsignado(c.getLote().getEstablecimiento(), u);
        if (c.getEstado() != EstadoCampania.SEMBRADA) {
            throw ErroresDeRegla.de("C5", "Solo se registran monitoreos en campañas sembradas");
        }
        Monitoreo m = new Monitoreo();
        m.setCampania(c);
        m.setPlagaId(r.plagaId());
        m.setIncidenciaPct(r.incidenciaPct());
        m.setFecha(r.fecha() != null ? r.fecha() : LocalDate.now());
        monitoreos.save(m);
        return new MonitoreoResponse(m.getId(), c.getId(), m.getPlagaId(), m.getIncidenciaPct(), m.getFecha());
    }

    public Campania buscar(Long id) {
        return campanias.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe la campaña " + id));
    }

    private CampaniaResponse aRespuesta(Campania c) {
        return new CampaniaResponse(c.getId(), c.getLote().getId(), c.getCultivoId(), c.getSuperficieSembradaHa(),
                c.getFechaSiembra(), c.getCosechaEstimada(), c.getEstado(), c.getMotivoPerdida(),
                c.getFechaSiembraReal(), c.getFechaCosecha(), c.getFechaPerdida());
    }
}
