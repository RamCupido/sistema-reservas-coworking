package com.ramses.reservas.reserva;

import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Una reserva semanal: agrupa varias {@link Reserva} normales (una por semana).
 * No guarda la lista de reservas (no hay @OneToMany): se consultan por serie_id cuando
 * hace falta, así cargar una reserva nunca arrastra a todas sus hermanas.
 */
@Entity
@Table(name = "series_reserva")
@Getter
public class SerieReserva {

    public static final int SEMANAS_MINIMAS = 2;
    public static final int SEMANAS_MAXIMAS = 12;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurso_id", nullable = false)
    private Recurso recurso;

    @Column(nullable = false)
    private int semanas;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    protected SerieReserva() {
    }

    public SerieReserva(Usuario usuario, Recurso recurso, int semanas) {
        this.usuario = usuario;
        this.recurso = recurso;
        this.semanas = semanas;
    }

    public boolean perteneceA(String email) {
        return usuario.getEmail().equals(email);
    }
}
