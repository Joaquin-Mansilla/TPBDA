package ar.edu.agro.campos.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Parte de la respuesta de Georef (GET /ubicacion) que usa el sistema. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeorefResponse(Ubicacion ubicacion) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ubicacion(Nombre provincia, Nombre departamento, Nombre municipio) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Nombre(String nombre) {
    }
}
