package ar.edu.agro.campos.controller;

import ar.edu.agro.campos.dto.CamposDtos.*;
import ar.edu.agro.campos.service.AplicacionService;
import ar.edu.agro.campos.service.CampaniaService;
import ar.edu.agro.campos.service.RiegoService;
import ar.edu.agro.campos.service.SeguimientoService;
import java.util.List;
import ar.edu.agro.campos.service.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campanias")
@Tag(name = "Campañas")
public class CampaniaController {
    private final CampaniaService campanias;
    private final AplicacionService aplicaciones;
    private final RiegoService riegos;
    private final SeguimientoService seguimiento;

    public CampaniaController(CampaniaService campanias, AplicacionService aplicaciones, RiegoService riegos,
                              SeguimientoService seguimiento) {
        this.campanias = campanias;
        this.aplicaciones = aplicaciones;
        this.riegos = riegos;
        this.seguimiento = seguimiento;
    }

    @PostMapping("/{id}/riegos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PRODUCTOR','OPERARIO')")
    @Operation(summary = "Registrar un riego: fecha (sin fecha es hoy) y lámina en mm (R1, C5, C8)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Riego registrado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (lámina mayor a 0 y hasta 200 mm)"),
            @ApiResponse(responseCode = "403", description = "No es el productor dueño ni el operario asignado (C6)"),
            @ApiResponse(responseCode = "404", description = "No existe la campaña"),
            @ApiResponse(responseCode = "422", description = "La campaña no está SEMBRADA (C5) o la fecha no va de la siembra a hoy (C8)")
    })
    public RiegoResponse registrarRiego(@PathVariable Long id, @Valid @RequestBody RiegoRequest r, @AuthenticationPrincipal Jwt jwt) {
        return riegos.registrar(id, r, Usuario.de(jwt));
    }

    @GetMapping("/{id}/riegos")
    @Operation(summary = "Listar los riegos de la campaña (C6)")
    public List<RiegoResponse> listarRiegos(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return riegos.listar(id, Usuario.de(jwt));
    }

    @GetMapping("/{id}/riego")
    @Operation(summary = "¿Toca regar? Estado de riego con los riegos de la campaña y las lluvias cargadas en el lote (R1)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "NO_REQUIERE, NO_SEMBRADA, AL_DIA, TOCA_REGAR o ATRASADO"),
            @ApiResponse(responseCode = "403", description = "Sin acceso al establecimiento (C6)"),
            @ApiResponse(responseCode = "404", description = "No existe la campaña"),
            @ApiResponse(responseCode = "503", description = "ms-tratamientos no disponible (hace falta la frecuencia de riego del cultivo)")
    })
    public EstadoRiegoResponse estadoRiego(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return riegos.consultarEstado(id, Usuario.de(jwt));
    }

    @GetMapping("/{id}/alertas")
    @Operation(summary = "Alertas de plagas: monitoreos que llegaron al umbral y si ya se aplicó contra esa plaga (M1)")
    public List<AlertaResponse> alertas(@PathVariable Long id,
                                        @RequestParam(name = "pendientes", defaultValue = "false") boolean soloPendientes,
                                        @AuthenticationPrincipal Jwt jwt) {
        return seguimiento.alertas(id, soloPendientes, Usuario.de(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una campaña con su estado, superficie y cosecha estimada (F04, C6)")
    public CampaniaResponse obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return campanias.obtener(id, Usuario.de(jwt));
    }

    @PostMapping("/{id}/siembra")
    @PreAuthorize("hasRole('PRODUCTOR')")
    @Operation(summary = "Registrar la siembra real: la campaña pasa a SEMBRADA y se recalcula la cosecha estimada (F05, C4, C5, C8)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Campaña SEMBRADA"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Solo el productor dueño (C6)"),
            @ApiResponse(responseCode = "404", description = "No existe la campaña"),
            @ApiResponse(responseCode = "409", description = "Otro usuario la modificó al mismo tiempo"),
            @ApiResponse(responseCode = "422", description = "No está PLANIFICADA (C5) o la fecha no es válida (C1, C4, C8)"),
            @ApiResponse(responseCode = "503", description = "ms-tratamientos no disponible (hace falta el ciclo del cultivo)")
    })
    public CampaniaResponse siembra(@PathVariable Long id, @Valid @RequestBody SiembraRequest r, @AuthenticationPrincipal Jwt jwt) {
        return campanias.registrarSiembra(id, r, Usuario.de(jwt));
    }

    @PostMapping("/{id}/cosecha")
    @PreAuthorize("hasRole('PRODUCTOR')")
    @Operation(summary = "Registrar la cosecha: la campaña pasa a COSECHADA (F05, C5, C8)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Campaña COSECHADA"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Solo el productor dueño (C6)"),
            @ApiResponse(responseCode = "404", description = "No existe la campaña"),
            @ApiResponse(responseCode = "409", description = "Otro usuario la modificó al mismo tiempo"),
            @ApiResponse(responseCode = "422", description = "No está SEMBRADA (C5) o la fecha no es válida (C8)")
    })
    public CampaniaResponse cosecha(@PathVariable Long id, @Valid @RequestBody CosechaRequest r, @AuthenticationPrincipal Jwt jwt) {
        return campanias.registrarCosecha(id, r, Usuario.de(jwt));
    }

    @PostMapping("/{id}/perdida")
    @PreAuthorize("hasRole('PRODUCTOR')")
    @Operation(summary = "Marcar la campaña como perdida, con fecha y motivo (F05, C5, C8)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Campaña PERDIDA"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o falta el motivo"),
            @ApiResponse(responseCode = "403", description = "Solo el productor dueño (C6)"),
            @ApiResponse(responseCode = "404", description = "No existe la campaña"),
            @ApiResponse(responseCode = "409", description = "Otro usuario la modificó al mismo tiempo"),
            @ApiResponse(responseCode = "422", description = "Ya está COSECHADA o PERDIDA (C5) o la fecha no es válida (C8)")
    })
    public CampaniaResponse perdida(@PathVariable Long id, @Valid @RequestBody PerdidaRequest r, @AuthenticationPrincipal Jwt jwt) {
        return campanias.registrarPerdida(id, r, Usuario.de(jwt));
    }

    @PostMapping("/{id}/monitoreos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PRODUCTOR','OPERARIO')")
    @Operation(summary = "Registrar la plaga observada y su incidencia (F06)")
    public MonitoreoResponse monitoreo(@PathVariable Long id, @Valid @RequestBody MonitoreoRequest r, @AuthenticationPrincipal Jwt jwt) {
        return campanias.registrarMonitoreo(id, r, Usuario.de(jwt));
    }

    @PostMapping("/{id}/aplicaciones")
    @PreAuthorize("hasAnyRole('PRODUCTOR','OPERARIO')")
    @Operation(summary = "Registrar una aplicación evaluada por ms-tratamientos (F07, proceso principal): "
            + "201 REGISTRADA, 422 rechazada (con fecha sugerida o motivos), 202 PENDIENTE_DE_VALIDACION (también si ms-tratamientos no responde)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "VIABLE: aplicación REGISTRADA"),
            @ApiResponse(responseCode = "202", description = "PENDIENTE_DE_VALIDACION (REVISION_MANUAL por L1 o ms-tratamientos no respondió)"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permiso o el establecimiento no es del usuario (C6)"),
            @ApiResponse(responseCode = "404", description = "No existe la campaña, el agroquímico, la plaga o el cultivo"),
            @ApiResponse(responseCode = "422", description = "Rechazada: regla de campos (C2, C5) o resultado REPROGRAMAR / NO_RECOMENDADO (no se guarda)"),
            @ApiResponse(responseCode = "503", description = "Base de datos no disponible")
    })
    public ResponseEntity<AplicacionResultadoResponse> registrarAplicacion(@PathVariable Long id,
            @Valid @RequestBody AplicacionRequest r, @AuthenticationPrincipal Jwt jwt) {
        var resultado = aplicaciones.registrar(id, r, Usuario.de(jwt));
        return ResponseEntity.status(resultado.status()).body(resultado.cuerpo());
    }
}
