package com.ramses.reservas.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Un único reloj inyectable para toda la app. En tests se reemplaza por Clock.fixed(...)
 * y así se puede probar lógica dependiente del tiempo (tokens, "reservas en el pasado").
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
