package ar.edu.agro.tratamientos.controller;

import ar.edu.agro.commons.contract.CultivoResumenResponse;
import ar.edu.agro.tratamientos.dto.CultivoRequest;
import ar.edu.agro.tratamientos.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cultivos")
@Tag(name = "Cultivos")
public class CultivoController {
    private final CatalogoService catalogo;

    public CultivoController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping
    @Operation(summary = "Listar los cultivos activos del catálogo (F12)")
    public List<CultivoResumenResponse> listar() {
        return catalogo.listarCultivos();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar familia, días de ciclo y ventana de siembra (F12, F04)")
    public CultivoResumenResponse obtener(@PathVariable Long id) {
        return catalogo.obtenerCultivo(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar un cultivo (F10, AGRONOMO)")
    public CultivoResumenResponse crear(@Valid @RequestBody CultivoRequest r) {
        return catalogo.crearCultivo(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modificar un cultivo (F10, AGRONOMO)")
    public CultivoResumenResponse modificar(@PathVariable Long id, @Valid @RequestBody CultivoRequest r) {
        return catalogo.modificarCultivo(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dar de baja un cultivo (baja lógica, F10, AGRONOMO)")
    public void baja(@PathVariable Long id) {
        catalogo.bajaCultivo(id);
    }
}
