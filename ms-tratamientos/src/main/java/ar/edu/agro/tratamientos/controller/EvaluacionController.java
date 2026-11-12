package ar.edu.agro.tratamientos.controller;

import ar.edu.agro.commons.contract.EvaluacionRequest;
import ar.edu.agro.commons.contract.EvaluacionResponse;
import ar.edu.agro.tratamientos.dto.EvaluacionDetalleResponse;
import ar.edu.agro.tratamientos.service.EvaluacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evaluaciones")
@Tag(name = "Evaluaciones")
public class EvaluacionController {
    private final EvaluacionService evaluaciones;

    public EvaluacionController(EvaluacionService evaluaciones) {
        this.evaluaciones = evaluaciones;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Evaluar una aplicación con T1 a T5 (F11). Lo invoca ms-campos reenviando el token del usuario; "
            + "una aplicación rechazada también es una evaluación exitosa (el resultado va en el cuerpo)")
    public EvaluacionResponse evaluar(@Valid @RequestBody EvaluacionRequest req, @AuthenticationPrincipal Jwt jwt) {
        return evaluaciones.evaluar(req, jwt.getSubject());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar una evaluación pasada con el pedido que se usó (F13, AGRONOMO)")
    public EvaluacionDetalleResponse obtener(@PathVariable Long id) {
        return evaluaciones.obtener(id);
    }
}
