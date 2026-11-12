package com.ramses.reservas.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Sin @Data: generaría equals/hashCode sobre todos los campos (problemático con
 * entidades JPA) y un toString que expondría el hash de la contraseña en los logs.
 * Los setters solo existen en los campos que tiene sentido modificar.
 */
@Entity
@Table(name = "usuarios")
@Getter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String email;

    @Setter
    @Column(nullable = false, length = 100)
    private String password;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    /** Requerido por JPA; protegido para que el código use el constructor completo. */
    protected Usuario() {
    }

    public Usuario(String nombre, String email, String passwordHash, Rol rol) {
        this.nombre = nombre;
        this.email = email;
        this.password = passwordHash;
        this.rol = rol;
    }
}
