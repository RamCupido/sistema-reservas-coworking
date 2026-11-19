package com.ramses.reservas.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableAsync activa @Async (los correos se envían en el pool "correo-*" que configura
 * spring.task.execution). Spring Boot crea ese pool automáticamente.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * @EnableScheduling activa los @Scheduled. Va en una clase aparte y condicional para poder
     * apagarlo en los tests (reservas.tareas.habilitadas=false): si no, una tarea podría
     * dispararse en medio de un test y cambiar datos sin que nadie la llame.
     */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "reservas.tareas", name = "habilitadas", havingValue = "true",
            matchIfMissing = true)
    static class SchedulingConfig {
    }
}
