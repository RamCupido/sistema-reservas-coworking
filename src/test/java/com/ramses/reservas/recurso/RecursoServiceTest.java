package com.ramses.reservas.recurso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ramses.reservas.common.exception.HorarioInvalidoException;
import com.ramses.reservas.common.exception.NombreRecursoDuplicadoException;
import com.ramses.reservas.common.exception.RecursoNoEncontradoException;
import com.ramses.reservas.recurso.dto.RecursoRequest;
import com.ramses.reservas.recurso.dto.RecursoResponse;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecursoServiceTest {

    @Mock
    private RecursoRepository recursoRepository;

    @InjectMocks
    private RecursoService recursoService;

    private static RecursoRequest request(String nombre, String apertura, String cierre, Boolean activo) {
        return new RecursoRequest(nombre, "desc", TipoRecurso.SALA_REUNIONES, 8,
                new BigDecimal("25.00"), LocalTime.parse(apertura), LocalTime.parse(cierre), activo);
    }

    private static Recurso recursoExistente(boolean activo) {
        return Recurso.builder().nombre("Sala Andes").tipo(TipoRecurso.SALA_REUNIONES).capacidad(8)
                .precioPorHora(new BigDecimal("25.00")).activo(activo)
                .horaApertura(LocalTime.of(8, 0)).horaCierre(LocalTime.of(20, 0)).build();
    }

    @Test
    void crearGuardaRecursoActivoPorDefectoConNombreSinEspacios() {
        when(recursoRepository.existsByNombreIgnoreCase("Sala Andes")).thenReturn(false);
        when(recursoRepository.save(any(Recurso.class))).thenAnswer(inv -> inv.getArgument(0));

        RecursoResponse respuesta = recursoService.crear(request("  Sala Andes ", "08:00", "20:00", null));

        ArgumentCaptor<Recurso> captor = ArgumentCaptor.forClass(Recurso.class);
        verify(recursoRepository).save(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("Sala Andes");
        assertThat(captor.getValue().isActivo()).isTrue();
        assertThat(respuesta.precioPorHora()).isEqualByComparingTo("25.00");
    }

    @Test
    void crearNormalizaElPrecioADosDecimales() {
        when(recursoRepository.save(any(Recurso.class))).thenAnswer(inv -> inv.getArgument(0));
        RecursoRequest sinDecimales = new RecursoRequest("Sala", null, TipoRecurso.SALA_REUNIONES, 8,
                new BigDecimal("30"), LocalTime.of(8, 0), LocalTime.of(20, 0), null);

        RecursoResponse respuesta = recursoService.crear(sinDecimales);

        assertThat(respuesta.precioPorHora()).hasToString("30.00");
    }

    @Test
    void crearConNombreDuplicadoLanzaConflicto() {
        when(recursoRepository.existsByNombreIgnoreCase("Sala Andes")).thenReturn(true);

        assertThatThrownBy(() -> recursoService.crear(request("Sala Andes", "08:00", "20:00", null)))
                .isInstanceOf(NombreRecursoDuplicadoException.class);
        verify(recursoRepository, never()).save(any());
    }

    @ParameterizedTest(name = "apertura {0} - cierre {1}")
    @CsvSource({
            "20:00, 08:00",   // apertura posterior al cierre
            "08:00, 08:00",   // apertura igual al cierre
            "08:15, 20:00",   // apertura no alineada a media hora
            "08:00, 19:45",   // cierre no alineado a media hora
            "08:00:30, 20:00" // con segundos
    })
    void crearConHorarioInvalidoLanzaExcepcion(String apertura, String cierre) {
        assertThatThrownBy(() -> recursoService.crear(request("Sala", apertura, cierre, null)))
                .isInstanceOf(HorarioInvalidoException.class);
        verify(recursoRepository, never()).save(any());
    }

    @Test
    void horarioConMediasHorasEsValido() {
        RecursoService.validarHorario(LocalTime.of(7, 30), LocalTime.of(22, 30));
    }

    @Test
    void obtenerInexistenteLanzaNoEncontrado() {
        when(recursoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recursoService.obtener(99L, true))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void obtenerInactivoEsInvisibleParaElPublicoPeroNoParaAdmin() {
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(recursoExistente(false)));

        assertThatThrownBy(() -> recursoService.obtener(1L, false))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(recursoService.obtener(1L, true).activo()).isFalse();
    }

    @Test
    void actualizarReemplazaCamposYMantieneActivoSiNoSeInforma() {
        Recurso existente = recursoExistente(true);
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(recursoRepository.existsByNombreIgnoreCaseAndIdNot("Sala Pacífico", 1L)).thenReturn(false);

        RecursoResponse respuesta = recursoService.actualizar(1L, request("Sala Pacífico", "09:00", "18:30", null));

        assertThat(existente.getNombre()).isEqualTo("Sala Pacífico");
        assertThat(existente.getHoraCierre()).isEqualTo(LocalTime.of(18, 30));
        assertThat(existente.isActivo()).isTrue();
        assertThat(respuesta.nombre()).isEqualTo("Sala Pacífico");
    }

    @Test
    void actualizarPermiteReactivarUnRecurso() {
        Recurso existente = recursoExistente(false);
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(existente));

        recursoService.actualizar(1L, request("Sala Andes", "08:00", "20:00", true));

        assertThat(existente.isActivo()).isTrue();
    }

    @Test
    void actualizarConNombreDeOtroRecursoLanzaConflicto() {
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(recursoExistente(true)));
        when(recursoRepository.existsByNombreIgnoreCaseAndIdNot("Sala Pacífico", 1L)).thenReturn(true);

        assertThatThrownBy(() -> recursoService.actualizar(1L, request("Sala Pacífico", "08:00", "20:00", null)))
                .isInstanceOf(NombreRecursoDuplicadoException.class);
    }

    @Test
    void actualizarInexistenteLanzaNoEncontrado() {
        when(recursoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recursoService.actualizar(99L, request("Sala", "08:00", "20:00", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void desactivarHaceBorradoLogico() {
        Recurso existente = recursoExistente(true);
        when(recursoRepository.findById(1L)).thenReturn(Optional.of(existente));

        recursoService.desactivar(1L);

        assertThat(existente.isActivo()).isFalse();
        verify(recursoRepository, never()).delete(any(Recurso.class));
        verify(recursoRepository, never()).deleteById(any());
    }
}
