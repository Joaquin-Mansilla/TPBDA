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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Campañas (F04, F05) y monitoreos (F06). Reglas C1 a C5; la colaboración con ms-tratamientos es para el cultivo. */
@Service
public class CampaniaService {

    private final CampaniaRepository campanias;
    private final MonitoreoRepository monitoreos;
    private final LoteService lotes;
    private final AccesoService acceso;
    private final TratamientosClient tratamientos;

    public CampaniaService(CampaniaRepository campanias, MonitoreoRepository monitoreos, LoteService lotes,
                           AccesoService acceso, TratamientosClient tratamientos) {
        this.campanias = campanias;
        this.monitoreos = monitoreos;
        this.lotes = lotes;
        this.acceso = acceso;
        this.tratamientos = tratamientos;
    }

    /**
     * F04. Pide el cultivo a ms-tratamientos (si no responde: 503 y la campaña no se crea) y valida C1 a C4.
     * Se informan todas las reglas incumplidas juntas.
     */
    @Transactional
    public CampaniaResponse planificar(Long loteId, CampaniaRequest r, Usuario u) {
        Lote lote = lotes.buscar(loteId);
        acceso.exigirDueno(lote.getEstablecimiento(), u);
        CultivoResumenResponse cultivo = tratamientos.obtenerCultivo(r.cultivoId());
        LocalDate cosecha = r.fechaSiembra().plusDays(cultivo.diasCiclo());

        // las campañas perdidas no ocupan el lote ni cuentan para la rotación
        List<Campania> vigentes = campanias.findByLoteIdOrderByFechaSiembraDesc(loteId).stream()
                .filter(c -> c.getEstado() != EstadoCampania.PERDIDA).toList();
        List<MotivoDto> motivos = new ArrayList<>();

        if (ReglasCampania.c1Superpone(r.fechaSiembra(), cosecha, vigentes.stream()
                .map(c -> new ReglasCampania.Rango(c.getFechaSiembra(), c.getCosechaEstimada())).toList())) {
            motivos.add(new MotivoDto("C1", "Las fechas se superponen con otra campaña del lote"));
        }
        if (!ReglasCampania.c2SuperficieValida(r.superficieSembradaHa(), lote.getSuperficieHa())) {
            motivos.add(new MotivoDto("C2", "La superficie sembrada (" + r.superficieSembradaHa()
                    + " ha) supera la del lote (" + lote.getSuperficieHa() + " ha)"));
        }
        List<Campania> anteriores = vigentes.stream().filter(c -> c.getFechaSiembra().isBefore(r.fechaSiembra())).limit(2).toList();
        if (anteriores.size() == 2) {
            List<String> familias = anteriores.stream()
                    .map(c -> tratamientos.obtenerCultivo(c.getCultivoId()).familia()).toList();
            if (!ReglasCampania.c3RotacionValida(cultivo.familia(), familias)) {
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
        c.setEstado(EstadoCampania.PLANIFICADA);
        campanias.save(c);
        return aRespuesta(c);
    }

    @Transactional(readOnly = true)
    public CampaniaResponse obtener(Long id, Usuario u) {
        Campania c = buscar(id);
        acceso.exigirLectura(c.getLote().getEstablecimiento(), u);
        return aRespuesta(c);
    }

    /** F05 (regla C5): PLANIFICADA a SEMBRADA o PERDIDA; SEMBRADA a COSECHADA o PERDIDA; perder exige motivo. */
    @Transactional
    public CampaniaResponse cambiarEstado(Long id, CambioEstadoRequest r, Usuario u) {
        Campania c = buscar(id);
        acceso.exigirDueno(c.getLote().getEstablecimiento(), u);
        if (!ReglasCampania.c5TransicionValida(c.getEstado(), r.estado())) {
            throw ErroresDeRegla.de("C5", "No se puede pasar de " + c.getEstado() + " a " + r.estado());
        }
        if (r.estado() == EstadoCampania.PERDIDA) {
            if (r.motivo() == null || r.motivo().isBlank()) {
                throw ErroresDeRegla.de("C5", "Para marcar la campaña como perdida hay que indicar el motivo");
            }
            c.setMotivoPerdida(r.motivo());
        }
        c.setEstado(r.estado());
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
                c.getFechaSiembra(), c.getCosechaEstimada(), c.getEstado(), c.getMotivoPerdida());
    }
}
