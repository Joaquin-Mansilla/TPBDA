package ar.edu.agro.campos.client;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Registra cada llamada saliente a una API externa con su estado y el tiempo que tardó. */
public class LlamadaExternaInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LlamadaExternaInterceptor.class);

    private final String api;

    public LlamadaExternaInterceptor(String api) {
        this.api = api;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long inicio = System.currentTimeMillis();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            log.info("{} {} {} -> {} ({} ms)", api, request.getMethod(), request.getURI(),
                    response.getStatusCode().value(), System.currentTimeMillis() - inicio);
            return response;
        } catch (IOException e) {
            log.warn("{} {} {} -> sin respuesta: {} ({} ms)", api, request.getMethod(), request.getURI(),
                    e.getMessage(), System.currentTimeMillis() - inicio);
            throw e;
        }
    }
}
