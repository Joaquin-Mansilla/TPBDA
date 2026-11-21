package ar.edu.agro.campos.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj de la aplicación (T16): las reglas que comparan con "hoy" lo reciben inyectado, así los tests usan uno fijo. */
@Configuration
public class RelojConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}
