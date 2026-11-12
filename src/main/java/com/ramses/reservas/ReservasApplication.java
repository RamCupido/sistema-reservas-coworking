package com.ramses.reservas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // registra los records @ConfigurationProperties (JwtProperties, AdminProperties)
public class ReservasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservasApplication.class, args);
    }
}
