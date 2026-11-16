package com.ramses.reservas.reserva;

import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Sin setters: el estado solo cambia mediante métodos con intención (cancelar()),
 * así una reserva nunca puede quedar en un estado incoherente desde fuera.
 */
@Entity
@Table(name = "reservas")
@Getter
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LAZY: no cargamos usuario/recurso salvo que se necesiten (las consultas usan @EntityGraph). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurso_id", nullable = false)
    private Recurso recurso;

    /** Instantes absolutos (UTC). La conversión a hora local del negocio se hace en el borde (mapper). */
    @Column(name = "fecha_inicio", nullable = false)
    private Instant fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private Instant fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado;

    /** Precio congelado al reservar: si luego cambia el precio del recurso, esta reserva no varía. */
    @Column(name = "precio_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioTotal;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    /**
     * Bloqueo optimista: Hibernate añade "WHERE version = ?" a cada UPDATE. Si otra
     * transacción modificó la fila entretanto, no se actualiza nada y lanza una excepción (→ 409).
     */
    @Version
    private Long version;

    protected Reserva() {
    }

    public Reserva(Usuario usuario, Recurso recurso, Instant fechaInicio, Instant fechaFin, BigDecimal precioTotal) {
        this.usuario = usuario;
        this.recurso = recurso;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.precioTotal = precioTotal;
        this.estado = EstadoReserva.CONFIRMADA;
    }

    public void cancelar() {
        this.estado = EstadoReserva.CANCELADA;
    }

    public boolean perteneceA(String email) {
        return usuario.getEmail().equals(email);
    }
}
