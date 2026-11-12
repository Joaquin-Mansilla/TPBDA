package ar.edu.agro.campos.controller;

import ar.edu.agro.campos.dto.CamposDtos.*;
import ar.edu.agro.campos.service.AplicacionService;
import ar.edu.agro.campos.service.CampaniaService;
import ar.edu.agro.campos.service.Usuario;
import io.swagger.v3.oas.annotations.Operation;
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

    public CampaniaController(CampaniaService campanias, AplicacionService aplicaciones) {
        this.campanias = campanias;
        this.aplicaciones = aplicaciones;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una campaña con su estado, superficie y cosecha estimada (F04, C6)")
    public CampaniaResponse obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return campanias.obtener(id, Usuario.de(jwt));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('PRODUCTOR')")
    @Operation(summary = "Marcar la campaña como sembrada, cosechada o perdida con motivo (F05, C5)")
    public CampaniaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest r, @AuthenticationPrincipal Jwt jwt) {
        return campanias.cambiarEstado(id, r, Usuario.de(jwt));
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
    public ResponseEntity<AplicacionResultadoResponse> registrarAplicacion(@PathVariable Long id,
            @Valid @RequestBody AplicacionRequest r, @AuthenticationPrincipal Jwt jwt) {
        var resultado = aplicaciones.registrar(id, r, Usuario.de(jwt));
        return ResponseEntity.status(resultado.status()).body(resultado.cuerpo());
    }
}
