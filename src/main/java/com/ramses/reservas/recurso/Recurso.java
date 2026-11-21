package com.ramses.reservas.recurso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "recursos")
@Getter
@Setter
public class Recurso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE) // el id lo asigna la BD; nadie debe cambiarlo
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoRecurso tipo;

    @Column(nullable = false)
    private Integer capacidad;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;

    protected Recurso() {
    }

    /** @Builder sobre el constructor (no la clase) para que el id nunca se pueda fijar a mano. */
    @Builder
    public Recurso(String nombre, String descripcion, TipoRecurso tipo, Integer capacidad,
                   boolean activo, LocalTime horaApertura, LocalTime horaCierre) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.tipo = tipo;
        this.capacidad = capacidad;
        this.activo = activo;
        this.horaApertura = horaApertura;
        this.horaCierre = horaCierre;
    }
}
