package ar.edu.agro.tratamientos.controller;

import ar.edu.agro.tratamientos.dto.PlagaRequest;
import ar.edu.agro.tratamientos.dto.PlagaResponse;
import ar.edu.agro.tratamientos.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plagas")
@Tag(name = "Plagas")
public class PlagaController {
    private final CatalogoService catalogo;

    public PlagaController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping
    @Operation(summary = "Listar las plagas activas (F12)")
    public List<PlagaResponse> listar() {
        return catalogo.listarPlagas();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar una plaga (F10, AGRONOMO)")
    public PlagaResponse crear(@Valid @RequestBody PlagaRequest r) {
        return catalogo.crearPlaga(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modificar una plaga (F10, AGRONOMO)")
    public PlagaResponse modificar(@PathVariable Long id, @Valid @RequestBody PlagaRequest r) {
        return catalogo.modificarPlaga(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Dar de baja una plaga (baja lógica, F10, AGRONOMO)")
    public void baja(@PathVariable Long id) {
        catalogo.bajaPlaga(id);
    }
}
