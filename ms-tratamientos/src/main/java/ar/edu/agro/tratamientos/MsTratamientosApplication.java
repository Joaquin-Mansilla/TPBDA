package ar.edu.agro.tratamientos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class MsTratamientosApplication {
    public static void main(String[] args) {
        // Las reglas comparan fechas; PostgreSQL no acepta el nombre antiguo de la zona
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        SpringApplication.run(MsTratamientosApplication.class, args);
    }
}
