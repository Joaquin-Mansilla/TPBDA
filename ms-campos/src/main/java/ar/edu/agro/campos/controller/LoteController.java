package ar.edu.agro.campos.controller;

import ar.edu.agro.campos.dto.CamposDtos.*;
import ar.edu.agro.campos.service.CampaniaService;
import ar.edu.agro.campos.service.LoteService;
import ar.edu.agro.campos.service.Usuario;
import io.swagger.v3.oas.annotations.Operation;
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

    public LoteController(LoteService lotes, CampaniaService campanias) {
        this.lotes = lotes;
        this.campanias = campanias;
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
    public CampaniaResponse planificar(@PathVariable Long id, @Valid @RequestBody CampaniaRequest r, @AuthenticationPrincipal Jwt jwt) {
        return campanias.planificar(id, r, Usuario.de(jwt));
    }
}
