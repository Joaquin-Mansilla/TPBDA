package ar.edu.agro.tratamientos.dto;

import java.math.BigDecimal;
import java.util.List;

public record AgroquimicoResponse(Long id, String nombre, String principioActivo, BigDecimal dosisMinHa,
                                  BigDecimal dosisMaxHa, String unidad, BigDecimal contenidoEnvase,
                                  BigDecimal precioEnvase, Integer diasCarencia, Integer intervaloMinDias,
                                  boolean activo, List<AgroquimicoRequest.HabilitacionDto> habilitaciones,
                                  List<AgroquimicoRequest.AjusteSueloDto> ajustesSuelo,
                                  List<AgroquimicoRequest.RestriccionDto> restricciones) {
}
