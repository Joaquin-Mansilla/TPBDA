package ar.edu.agro.tratamientos.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** TEMPORAL: verifica el enrutamiento del Gateway y la validación del JWT. Se borra al tener los endpoints reales. */
@RestController
public class PingController {

    @GetMapping("/api/v1/cultivos/ping")
    public ResponseEntity<Map<String, Object>> ping(Authentication auth) {
        return ResponseEntity.ok(Map.of(
                "servicio", "ms-tratamientos",
                "sub", auth.getName(),
                "roles", auth.getAuthorities().toString()));
    }
}
