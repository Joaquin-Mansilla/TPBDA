package ar.edu.agro.campos.config;

import ar.edu.agro.campos.client.LlamadaExternaInterceptor;
import ar.edu.agro.campos.client.ReenvioTokenInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient hacia ms-tratamientos. La llamada va directo por la red interna de Docker (no pasa por el
 * Gateway) y reenvía el JWT del usuario que originó el pedido.
 */
@Configuration
@EnableConfigurationProperties(TratamientosProperties.class)
public class ClienteTratamientosConfig {

    @Bean
    RestClient tratamientosRestClient(RestClient.Builder builder, TratamientosProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.timeoutConexion().toMillis());
        factory.setReadTimeout((int) props.timeoutLectura().toMillis());

        return builder
                .baseUrl(props.urlBase())
                .requestFactory(factory)
                // El primero que se agrega es el más externo: así el log ve el estado final de la llamada
                .requestInterceptor(new LlamadaExternaInterceptor("Tratamientos"))
                .requestInterceptor(new ReenvioTokenInterceptor())
                .build();
    }
}
