package ar.edu.agro.tratamientos.controller;

import ar.edu.agro.tratamientos.dto.AgroquimicoRequest;
import ar.edu.agro.tratamientos.dto.AgroquimicoResponse;
import ar.edu.agro.tratamientos.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agroquimicos")
@Tag(name = "Agroquímicos")
public class AgroquimicoController {
    private final CatalogoService catalogo;

    public AgroquimicoController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping
    @Operation(summary = "Listar los agroquímicos activos (F12)")
    public List<AgroquimicoResponse> listar() {
        return catalogo.listarAgroquimicos();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ficha de un agroquímico: dosis, carencia, intervalo, habilitaciones, ajustes y restricciones (F12)")
    public AgroquimicoResponse obtener(@PathVariable Long id) {
        return catalogo.obtenerAgroquimico(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar un agroquímico completo (F10, AGRONOMO)")
    public AgroquimicoResponse crear(@Valid @RequestBody AgroquimicoRequest r) {
        return catalogo.crearAgroquimico(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modificar la ficha de un agroquímico (F10, AGRONOMO)")
    public AgroquimicoResponse modificar(@PathVariable Long id, @Valid @RequestBody AgroquimicoRequest r) {
        return catalogo.modificarAgroquimico(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dar de baja un agroquímico (baja lógica, F10, AGRONOMO)")
    public void baja(@PathVariable Long id) {
        catalogo.bajaAgroquimico(id);
    }
}
