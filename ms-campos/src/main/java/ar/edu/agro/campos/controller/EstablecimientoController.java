package ar.edu.agro.campos.controller;

import ar.edu.agro.campos.dto.CamposDtos.*;
import ar.edu.agro.campos.service.EstablecimientoService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/establecimientos")
@Tag(name = "Establecimientos y lotes")
public class EstablecimientoController {
    private final EstablecimientoService establecimientos;
    private final LoteService lotes;

    public EstablecimientoController(EstablecimientoService establecimientos, LoteService lotes) {
        this.establecimientos = establecimientos;
        this.lotes = lotes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRODUCTOR')")
    @Operation(summary = "Registrar un establecimiento; quien lo crea queda como dueño (F01)")
    public EstablecimientoResponse crear(@Valid @RequestBody EstablecimientoRequest r, @AuthenticationPrincipal Jwt jwt) {
        return establecimientos.crear(r, Usuario.de(jwt));
    }

    @GetMapping
    @Operation(summary = "Listar los establecimientos visibles para el usuario (F01, C6)")
    public List<EstablecimientoResponse> listar(@AuthenticationPrincipal Jwt jwt) {
        return establecimientos.listar(Usuario.de(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un establecimiento con sus lotes y operarios (F01, C6)")
    public EstablecimientoResponse obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return establecimientos.obtener(id, Usuario.de(jwt));
    }

    @PostMapping("/{id}/lotes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRODUCTOR')")
    @Operation(summary = "Registrar un lote y completar su suelo (SoilGrids) y ubicación (Georef) (F02, L1)")
    public LoteResponse crearLote(@PathVariable Long id, @Valid @RequestBody LoteRequest r, @AuthenticationPrincipal Jwt jwt) {
        return lotes.crear(id, r, Usuario.de(jwt));
    }
}
