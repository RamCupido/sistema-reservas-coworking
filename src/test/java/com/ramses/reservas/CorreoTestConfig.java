package com.ramses.reservas;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Sustituye el JavaMailSender real: el de Spring Boot es @ConditionalOnMissingBean, así que al
 * existir este no se crea el otro y ningún test intenta conectar a un SMTP.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CorreoTestConfig {

    @Bean
    CorreoFalso correoFalso() {
        return new CorreoFalso();
    }
}
