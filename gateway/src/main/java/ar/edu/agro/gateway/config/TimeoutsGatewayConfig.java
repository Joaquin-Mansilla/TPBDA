package ar.edu.agro.gateway.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * T12: tiempos máximos hacia los microservicios. Sin ellos un microservicio colgado dejaría el pedido esperando
 * indefinidamente. Al vencer, ErroresGatewayHandler responde 504.
 *
 * El tiempo de respuesta tiene que ser MAYOR que lo más lento que puede tardar ms-campos con todo funcionando:
 * planificar una campaña puede hacer hasta 3 consultas a ms-tratamientos (timeout de lectura 5 s, con un reintento
 * cada una) y crear un lote consulta SoilGrids y Georef (hasta unos 25 s con reintentos). Por eso 30 s por defecto.
 * Se cambian con AGRO_GATEWAY_CONNECT_TIMEOUT y AGRO_GATEWAY_RESPONSE_TIMEOUT (por ejemplo 3s y 30s).
 */
@Configuration
public class TimeoutsGatewayConfig {

    @Bean
    HttpClientCustomizer timeoutsHaciaMicroservicios(
            @Value("${agro.gateway.connect-timeout:3s}") Duration conexion,
            @Value("${agro.gateway.response-timeout:30s}") Duration respuesta) {
        return httpClient -> httpClient
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) conexion.toMillis())
                .responseTimeout(respuesta);
    }
}
