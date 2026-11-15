package ar.edu.agro.campos.controller;

import ar.edu.agro.campos.dto.CamposDtos.*;
import ar.edu.agro.campos.model.EstadoAplicacion;
import ar.edu.agro.campos.service.AplicacionService;
import ar.edu.agro.campos.service.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/aplicaciones")
@Tag(name = "Aplicaciones")
public class AplicacionController {
    private final AplicacionService aplicaciones;

    public AplicacionController(AplicacionService aplicaciones) {
        this.aplicaciones = aplicaciones;
    }

    @GetMapping
    @Operation(summary = "Listar aplicaciones por estado; es la bandeja de pendientes del agrónomo (F08)")
    public List<AplicacionResponse> listar(@RequestParam(required = false) EstadoAplicacion estado, @AuthenticationPrincipal Jwt jwt) {
        return aplicaciones.listar(estado, Usuario.de(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una aplicación (F08, C6)")
    public AplicacionResponse obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return aplicaciones.obtener(id, Usuario.de(jwt));
    }

    @PostMapping("/{id}/autorizacion")
    @PreAuthorize("hasRole('AGRONOMO')")
    @Operation(summary = "Autorizar manualmente una aplicación pendiente; la justificación es obligatoria (F09, A1)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AUTORIZADA_MANUALMENTE"),
            @ApiResponse(responseCode = "400", description = "Falta la justificación"),
            @ApiResponse(responseCode = "403", description = "Solo AGRONOMO"),
            @ApiResponse(responseCode = "404", description = "No existe la aplicación"),
            @ApiResponse(responseCode = "409", description = "Otro agrónomo la resolvió al mismo tiempo"),
            @ApiResponse(responseCode = "422", description = "La aplicación no está pendiente (A1)")
    })
    public AplicacionResponse autorizar(@PathVariable Long id, @Valid @RequestBody AutorizacionRequest r, @AuthenticationPrincipal Jwt jwt) {
        return aplicaciones.autorizar(id, r, Usuario.de(jwt));
    }

    @PostMapping("/{id}/reevaluacion")
    @PreAuthorize("hasRole('AGRONOMO')")
    @Operation(summary = "Volver a evaluar una aplicación pendiente con ms-tratamientos (F09). 503 si no responde")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "VIABLE: pasa a REGISTRADA"),
            @ApiResponse(responseCode = "202", description = "Sigue pendiente (REVISION_MANUAL)"),
            @ApiResponse(responseCode = "403", description = "Solo AGRONOMO"),
            @ApiResponse(responseCode = "404", description = "No existe la aplicación"),
            @ApiResponse(responseCode = "409", description = "Otro agrónomo la resolvió al mismo tiempo"),
            @ApiResponse(responseCode = "422", description = "No está pendiente (A1) o el resultado es REPROGRAMAR / NO_RECOMENDADO (sigue pendiente)"),
            @ApiResponse(responseCode = "503", description = "ms-tratamientos no disponible"),
            @ApiResponse(responseCode = "504", description = "ms-tratamientos no respondió a tiempo")
    })
    public ResponseEntity<AplicacionResultadoResponse> reevaluar(@PathVariable Long id) {
        var resultado = aplicaciones.reevaluar(id);
        return ResponseEntity.status(resultado.status()).body(resultado.cuerpo());
    }
}
