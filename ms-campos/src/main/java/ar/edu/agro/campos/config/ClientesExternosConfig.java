package ar.edu.agro.campos.config;

import ar.edu.agro.campos.client.LlamadaExternaInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Un RestClient por API externa: URL base y timeouts configurables, y un interceptor que registra
 * cada llamada saliente con su estado y su tiempo.
 */
@Configuration
@EnableConfigurationProperties(ApisExternasProperties.class)
public class ClientesExternosConfig {

    @Bean
    RestClient soilGridsRestClient(RestClient.Builder builder, ApisExternasProperties props) {
        ApisExternasProperties.SoilGrids cfg = props.soilgrids();
        return builder
                .baseUrl(cfg.urlBase())
                .requestFactory(factory(cfg.timeoutConexion().toMillis(), cfg.timeoutLectura().toMillis()))
                .requestInterceptor(new LlamadaExternaInterceptor("SoilGrids"))
                .build();
    }

    @Bean
    RestClient georefRestClient(RestClient.Builder builder, ApisExternasProperties props) {
        ApisExternasProperties.Georef cfg = props.georef();
        return builder
                .baseUrl(cfg.urlBase())
                .requestFactory(factory(cfg.timeoutConexion().toMillis(), cfg.timeoutLectura().toMillis()))
                .requestInterceptor(new LlamadaExternaInterceptor("Georef"))
                .build();
    }

    private SimpleClientHttpRequestFactory factory(long conexionMs, long lecturaMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) conexionMs);
        factory.setReadTimeout((int) lecturaMs);
        return factory;
    }
}
