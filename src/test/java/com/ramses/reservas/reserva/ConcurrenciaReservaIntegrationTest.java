package com.ramses.reservas.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ramses.reservas.IntegracionTest;
import com.ramses.reservas.LimpiadorBaseDatos;
import com.ramses.reservas.common.exception.ConflictoException;
import com.ramses.reservas.common.exception.ConflictoReservaException;
import com.ramses.reservas.recurso.Recurso;
import com.ramses.reservas.recurso.RecursoRepository;
import com.ramses.reservas.recurso.TipoRecurso;
import com.ramses.reservas.reserva.dto.CrearReservaRequest;
import com.ramses.reservas.reserva.dto.CrearSerieRequest;
import com.ramses.reservas.usuario.Rol;
import com.ramses.reservas.usuario.Usuario;
import com.ramses.reservas.usuario.UsuarioRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Regla 2: dos usuarios no pueden reservar el mismo horario al mismo tiempo.
 * <p>
 * Importante: esta clase NO es @Transactional. Cada hilo debe abrir su propia transacción
 * real contra PostgreSQL, igual que dos peticiones HTTP simultáneas en producción.
 */
@IntegracionTest
class ConcurrenciaReservaIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Guayaquil");
    private static final int HILOS = 10;

    @Autowired
    private ReservaService reservaService;
    @Autowired
    private SerieReservaService serieReservaService;
    @Autowired
    private ReservaRepository reservaRepository;
    @Autowired
    private SerieReservaRepository serieReservaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private RecursoRepository recursoRepository;
    @Autowired
    private LimpiadorBaseDatos limpiadorBaseDatos;

    private Recurso sala;
    private final LocalDate dia = LocalDate.now(ZONA).plusDays(5);

    @BeforeEach
    void setUp() {
        limpiadorBaseDatos.limpiar();
        for (int i = 0; i < HILOS; i++) {
            usuarioRepository.save(new Usuario("Usuario " + i, "usuario" + i + "@mail.com", "x", Rol.CLIENTE));
        }
        sala = recursoRepository.save(Recurso.builder().nombre("Sala Concurrida").tipo(TipoRecurso.SALA_REUNIONES)
                .capacidad(4).activo(true)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build());
    }

    @Test
    void deDiezPeticionesSimultaneasParaElMismoHorarioSoloGanaUna() throws Exception {
        CrearReservaRequest mismoHorario = new CrearReservaRequest(sala.getId(), dia.atTime(10, 0), dia.atTime(11, 0));
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);

        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            String email = "usuario" + i + "@mail.com";
            resultados.add(pool.submit(() -> {
                salida.await(); // todos esperan aquí y arrancan a la vez
                try {
                    reservaService.crear(mismoHorario, email);
                    return true;
                } catch (ConflictoReservaException e) {
                    return false;
                }
            }));
        }
        salida.countDown();

        int exitos = 0;
        int conflictos = 0;
        for (Future<Boolean> resultado : resultados) {
            // get() relanza cualquier excepción inesperada (que haría fallar el test)
            if (resultado.get(30, TimeUnit.SECONDS)) {
                exitos++;
            } else {
                conflictos++;
            }
        }
        pool.shutdown();

        assertThat(exitos).isEqualTo(1);
        assertThat(conflictos).isEqualTo(HILOS - 1);
        assertThat(reservaRepository.count()).isEqualTo(1);
    }

    /**
     * Todo o nada también bajo concurrencia: si una serie pierde la carrera en una sola semana,
     * el rollback deshace la serie entera (ni reservas sueltas ni la fila de la serie).
     */
    @Test
    void deDiezSeriesSimultaneasParaLasMismasSemanasSoloGanaUnaCompleta() throws Exception {
        CrearSerieRequest mismaSerie = new CrearSerieRequest(sala.getId(), dia.atTime(10, 0), dia.atTime(11, 0), 4);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(HILOS);

        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            String email = "usuario" + i + "@mail.com";
            resultados.add(pool.submit(() -> {
                salida.await();
                try {
                    serieReservaService.crear(mismaSerie, email);
                    return true;
                } catch (ConflictoException e) {
                    // ConflictoSerieException si vio la serie ganadora ya guardada;
                    // ConflictoReservaException si la detectó el constraint durante el INSERT.
                    return false;
                }
            }));
        }
        salida.countDown();

        int exitos = 0;
        for (Future<Boolean> resultado : resultados) {
            if (resultado.get(30, TimeUnit.SECONDS)) {
                exitos++;
            }
        }
        pool.shutdown();

        assertThat(exitos).isEqualTo(1);
        assertThat(reservaRepository.count()).isEqualTo(4);
        assertThat(serieReservaRepository.count()).isEqualTo(1);
    }

    @Test
    void elConstraintExcludeRechazaSolapamientosAunqueSeSalteElService() {
        insertarDirecto(dia.atTime(10, 0), dia.atTime(11, 0));

        assertThatThrownBy(() -> insertarDirecto(dia.atTime(10, 30), dia.atTime(11, 30)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(ReservaService.esViolacionDeSolapamiento(e)).isTrue());
    }

    @Test
    void elConstraintExcludePermiteRangosContiguos() {
        insertarDirecto(dia.atTime(10, 0), dia.atTime(11, 0));
        insertarDirecto(dia.atTime(11, 0), dia.atTime(12, 0)); // '[)': el fin no está incluido

        assertThat(reservaRepository.count()).isEqualTo(2);
    }

    @Test
    void elConstraintExcludeIgnoraLasCanceladas() {
        Reserva cancelada = insertarDirecto(dia.atTime(10, 0), dia.atTime(11, 0));
        cancelada.cancelar();
        reservaRepository.saveAndFlush(cancelada);

        insertarDirecto(dia.atTime(10, 0), dia.atTime(11, 0));

        assertThat(reservaRepository.count()).isEqualTo(2);
    }

    private Reserva insertarDirecto(LocalDateTime inicio, LocalDateTime fin) {
        Usuario usuario = usuarioRepository.findByEmail("usuario0@mail.com").orElseThrow();
        Instant desde = inicio.atZone(ZONA).toInstant();
        Instant hasta = fin.atZone(ZONA).toInstant();
        return reservaRepository.saveAndFlush(new Reserva(usuario, sala, desde, hasta));
    }
}
