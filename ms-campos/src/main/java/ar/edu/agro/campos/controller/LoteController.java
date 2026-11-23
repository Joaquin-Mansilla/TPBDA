package ar.edu.agro.campos.controller;

import ar.edu.agro.campos.dto.CamposDtos.*;
import ar.edu.agro.campos.service.CampaniaService;
import ar.edu.agro.campos.service.LoteService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lotes")
@Tag(name = "Establecimientos y lotes")
public class LoteController {
    private final LoteService lotes;
    private final CampaniaService campanias;
    private final SeguimientoService seguimiento;
    private final RiegoService riegos;

    public LoteController(LoteService lotes, CampaniaService campanias, SeguimientoService seguimiento,
                          RiegoService riegos) {
        this.lotes = lotes;
        this.campanias = campanias;
        this.seguimiento = seguimiento;
        this.riegos = riegos;
    }

    @PostMapping("/{id}/lluvias")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PRODUCTOR','OPERARIO')")
    @Operation(summary = "Cargar a mano la lluvia de un día en el lote: fecha (sin fecha es hoy) y mm, 0 si no llovió (R1, C8)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lluvia cargada; cuenta para todas las campañas del lote"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (mm de 0 a 500)"),
            @ApiResponse(responseCode = "403", description = "No es el productor dueño ni el operario asignado (C6)"),
            @ApiResponse(responseCode = "404", description = "No existe el lote"),
            @ApiResponse(responseCode = "422", description = "Fecha futura (C8)")
    })
    public LluviaResponse registrarLluvia(@PathVariable Long id, @Valid @RequestBody LluviaRequest r,
                                          @AuthenticationPrincipal Jwt jwt) {
        return riegos.registrarLluvia(id, r, Usuario.de(jwt));
    }

    @GetMapping("/{id}/lluvias")
    @Operation(summary = "Lluvias cargadas en el lote (C6)")
    public List<LluviaResponse> listarLluvias(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return riegos.listarLluvias(id, Usuario.de(jwt));
    }

    @GetMapping("/{id}/historial")
    @Operation(summary = "Historial del lote: campañas con monitoreos, alertas, aplicaciones, riegos y rendimiento por cultivo (C6)")
    public HistorialLoteResponse historial(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return seguimiento.historial(id, Usuario.de(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un lote con sus datos de suelo y ubicación (F02, C6)")
    public LoteResponse obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return lotes.obtener(id, Usuario.de(jwt));
    }

    @PatchMapping("/{id}/suelo")
    @PreAuthorize("hasRole('AGRONOMO')")
    @Operation(summary = "Cargar o corregir a mano los datos de suelo de un lote (F03)")
    public LoteResponse actualizarSuelo(@PathVariable Long id, @Valid @RequestBody SueloRequest r) {
        return lotes.actualizarSuelo(id, r);
    }

    @PostMapping("/{id}/suelo/refrescar")
    @PreAuthorize("hasRole('AGRONOMO')")
    @Operation(summary = "Volver a consultar el suelo y la ubicación del lote (F03). 503 si ninguna API responde")
    public LoteResponse refrescar(@PathVariable Long id) {
        return lotes.refrescar(id);
    }

    @PostMapping("/{id}/campanias")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRODUCTOR')")
    @Operation(summary = "Planificar una campaña: valida C1 a C4 con el cultivo de ms-tratamientos (F04). 503 si ms-tratamientos no responde")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Campaña PLANIFICADA"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Solo el productor dueño (C6)"),
            @ApiResponse(responseCode = "404", description = "No existe el lote o el cultivo"),
            @ApiResponse(responseCode = "422", description = "No cumple C1 a C4"),
            @ApiResponse(responseCode = "503", description = "ms-tratamientos no disponible"),
            @ApiResponse(responseCode = "504", description = "ms-tratamientos no respondió a tiempo")
    })
    public CampaniaResponse planificar(@PathVariable Long id, @Valid @RequestBody CampaniaRequest r, @AuthenticationPrincipal Jwt jwt) {
        return campanias.planificar(id, r, Usuario.de(jwt));
    }
}
