package ar.edu.agro.tratamientos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlagaRequest(@NotBlank @Size(max = 80) String nombre) {
}
