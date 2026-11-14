package ar.edu.agro.tratamientos.service;

import ar.edu.agro.commons.contract.*;
import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import ar.edu.agro.tratamientos.dto.EvaluacionDetalleResponse;
import ar.edu.agro.tratamientos.model.*;
import ar.edu.agro.tratamientos.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Proceso de evaluación (F11): aplica T1 a T5 y L1, calcula costo y guarda la decisión con su snapshot. */
@Service
public class EvaluacionService {

    private final AgroquimicoRepository agroquimicos;
    private final CultivoRepository cultivos;
    private final PlagaRepository plagas;
    private final HabilitacionRepository habilitaciones;
    private final AjusteSueloRepository ajustes;
    private final RestriccionTerritorialRepository restricciones;
    private final EvaluacionRepository evaluaciones;
    private final ObjectMapper mapper;

    public EvaluacionService(AgroquimicoRepository agroquimicos, CultivoRepository cultivos, PlagaRepository plagas,
                             HabilitacionRepository habilitaciones, AjusteSueloRepository ajustes,
                             RestriccionTerritorialRepository restricciones, EvaluacionRepository evaluaciones,
                             ObjectMapper mapper) {
        this.agroquimicos = agroquimicos;
        this.cultivos = cultivos;
        this.plagas = plagas;
        this.habilitaciones = habilitaciones;
        this.ajustes = ajustes;
        this.restricciones = restricciones;
        this.evaluaciones = evaluaciones;
        this.mapper = mapper;
    }

    @Transactional
    public EvaluacionResponse evaluar(EvaluacionRequest req, String solicitadaPorSub) {
        Agroquimico agro = agroquimicos.findById(req.agroquimicoId()).filter(Agroquimico::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el agroquímico " + req.agroquimicoId()));
        cultivos.findById(req.cultivoId()).filter(Cultivo::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cultivo " + req.cultivoId()));
        plagas.findById(req.plagaId()).filter(Plaga::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la plaga " + req.plagaId()));

        List<MotivoDto> motivos = new ArrayList<>();
        LocalDate fechaSugerida = null;

        // T1
        ReglasEvaluacion.t1Habilitacion(habilitaciones.existsByAgroquimicoIdAndCultivoIdAndPlagaId(
                agro.getId(), req.cultivoId(), req.plagaId())).ifPresent(motivos::add);

        // T3 (usa el historial que manda ms-campos)
        // ms-campos manda solo las aplicaciones previas de este mismo producto en el lote: alcanza con la última fecha
        LocalDate ultima = null;
        for (AplicacionPreviaDto previa : req.aplicacionesPrevias()) {
            if (ultima == null || previa.fecha().isAfter(ultima)) {
                ultima = previa.fecha();
            }
        }
        var t3 = ReglasEvaluacion.t3Intervalo(req.fechaAplicacion(), ultima, agro.getIntervaloMinDias());
        if (t3.isPresent()) {
            fechaSugerida = t3.get();
            motivos.add(new MotivoDto("T3", "Pasaron menos de " + agro.getIntervaloMinDias()
                    + " días desde la última aplicación (" + ultima + "); la primera fecha posible es " + fechaSugerida));
        }

        // T4
        ReglasEvaluacion.t4Carencia(req.fechaAplicacion(), agro.getDiasCarencia(), req.cosechaEstimada())
                .ifPresent(motivos::add);

        // T2 (necesita el suelo) y T5 (necesita la ubicación); si faltan, es L1: revisión manual
        boolean sinSuelo = req.suelo() == null || req.suelo().claseTextura() == null;
        boolean sinUbicacion = req.ubicacion() == null || req.ubicacion().provincia() == null
                || req.ubicacion().provincia().isBlank();
        if (!sinSuelo) {
            BigDecimal factor = ajustes.findByAgroquimicoIdAndClaseTextura(agro.getId(), req.suelo().claseTextura())
                    .map(AjusteSuelo::getFactorDosisMax).orElse(BigDecimal.ONE);
            ReglasEvaluacion.t2Dosis(req.dosisPorHa(), agro.getDosisMinHa(), agro.getDosisMaxHa(), factor)
                    .ifPresent(motivos::add);
        }
        if (!sinUbicacion) {
            List<ReglasEvaluacion.Restriccion> lista = restricciones.findByAgroquimicoId(agro.getId()).stream()
                    .map(r -> new ReglasEvaluacion.Restriccion(r.getProvincia(), r.getDepartamento())).toList();
            ReglasEvaluacion.t5Restriccion(req.ubicacion().provincia(), req.ubicacion().departamento(), lista)
                    .ifPresent(motivos::add);
        }

        ResultadoEvaluacion resultado;
        if (!motivos.isEmpty()) {
            // Solo el intervalo incumplido se puede arreglar cambiando la fecha: REPROGRAMAR
            boolean soloReprograma = motivos.size() == 1 && motivos.get(0).regla().equals("T3");
            resultado = soloReprograma ? ResultadoEvaluacion.REPROGRAMAR : ResultadoEvaluacion.NO_RECOMENDADO;
            if (!soloReprograma) {
                fechaSugerida = null;
            }
        } else if (sinSuelo || sinUbicacion) {
            resultado = ResultadoEvaluacion.REVISION_MANUAL;
            motivos.add(new MotivoDto("L1", "Faltan datos del lote ("
                    + (sinSuelo ? "suelo" : "") + (sinSuelo && sinUbicacion ? " y " : "") + (sinUbicacion ? "ubicación" : "")
                    + "): requiere revisión de un agrónomo"));
        } else {
            resultado = ResultadoEvaluacion.VIABLE;
        }

        var costo = ReglasEvaluacion.calcularCosto(req.dosisPorHa(), req.superficieTratadaHa(),
                agro.getContenidoEnvase(), agro.getPrecioEnvase());

        Evaluacion ev = new Evaluacion();
        ev.setFechaEvaluacion(LocalDateTime.now());
        ev.setAgroquimicoId(agro.getId());
        ev.setCultivoId(req.cultivoId());
        ev.setPlagaId(req.plagaId());
        ev.setFechaAplicacion(req.fechaAplicacion());
        ev.setDosisPorHa(req.dosisPorHa());
        ev.setSuperficieHa(req.superficieTratadaHa());
        ev.setResultado(resultado);
        ev.setFechaSugerida(fechaSugerida);
        ev.setDosisTotal(costo.dosisTotal());
        ev.setEnvases(costo.envases());
        ev.setCosto(costo.costo());
        ev.setSolicitadaPorSub(solicitadaPorSub);
        try {
            ev.setSnapshotPedido(mapper.writeValueAsString(req));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar el snapshot de la evaluación", e);
        }
        for (MotivoDto m : motivos) {
            EvaluacionMotivo em = new EvaluacionMotivo();
            em.setEvaluacion(ev);
            em.setRegla(m.regla());
            em.setDetalle(m.detalle().length() > 255 ? m.detalle().substring(0, 255) : m.detalle());
            ev.getMotivos().add(em);
        }
        evaluaciones.save(ev);
        return aRespuesta(ev);
    }

    @Transactional(readOnly = true)
    public EvaluacionDetalleResponse obtener(Long id) {
        Evaluacion ev = evaluaciones.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la evaluación " + id));
        try {
            return new EvaluacionDetalleResponse(aRespuesta(ev), ev.getFechaEvaluacion(), ev.getAgroquimicoId(),
                    ev.getCultivoId(), ev.getPlagaId(), ev.getFechaAplicacion(), ev.getDosisPorHa(),
                    ev.getSuperficieHa(), ev.getSolicitadaPorSub(), mapper.readTree(ev.getSnapshotPedido()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Snapshot ilegible en la evaluación " + id, e);
        }
    }

    private EvaluacionResponse aRespuesta(Evaluacion ev) {
        List<MotivoDto> motivos = ev.getMotivos().stream().map(m -> new MotivoDto(m.getRegla(), m.getDetalle())).toList();
        return new EvaluacionResponse(ev.getId(), ev.getResultado(), motivos, ev.getFechaSugerida(), ev.getDosisTotal(),
                ev.getEnvases(), ev.getCosto());
    }
}
