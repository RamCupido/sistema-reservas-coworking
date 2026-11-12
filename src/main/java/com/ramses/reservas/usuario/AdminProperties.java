package com.ramses.reservas.usuario;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        String email,
        String password,
        @DefaultValue("Administrador") String nombre) {

    public boolean estaConfigurado() {
        return StringUtils.hasText(email) && StringUtils.hasText(password);
    }
}
