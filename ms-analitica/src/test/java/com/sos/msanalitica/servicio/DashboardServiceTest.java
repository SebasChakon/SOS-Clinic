package com.sos.msanalitica.servicio;

import com.sos.msanalitica.dto.CapacidadDto;
import com.sos.msanalitica.dto.ConteoDto;
import com.sos.msanalitica.dto.HoraDto;
import com.sos.msanalitica.dto.LlegadasDto;
import com.sos.msanalitica.dto.ResumenDto;
import com.sos.msanalitica.repositorio.DashboardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardRepository repository;

    @InjectMocks
    private DashboardService service;

    @Test
    void resumenConservaLosFiltrosDelPeriodo() {
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 31);
        ResumenDto esperado = new ResumenDto(12, 35.5);
        when(repository.obtenerResumen(desde, hasta, "Norte", "Mañana")).thenReturn(esperado);

        assertEquals(esperado, service.obtenerResumen(desde, hasta, "Norte", "Mañana"));
    }

    @Test
    void llegadasAgrupaLasTresDistribucionesDelRepositorio() {
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 31);
        List<HoraDto> horas = List.of(new HoraDto(8, 4));
        List<ConteoDto> prioridades = List.of(new ConteoDto("Triage 1 (Rojo)", 2));
        List<ConteoDto> localidades = List.of(new ConteoDto("Norte", 4));
        when(repository.obtenerPorHora(desde, hasta, null, null)).thenReturn(horas);
        when(repository.obtenerPorPrioridad(desde, hasta, null, null)).thenReturn(prioridades);
        when(repository.obtenerPorLocalidad(desde, hasta, null, null)).thenReturn(localidades);

        assertEquals(new LlegadasDto(horas, prioridades, localidades),
                service.obtenerLlegadas(desde, hasta, null, null));
    }

    @Test
    void capacidadNoRecibeFiltroDeLocalidad() {
        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 31);
        List<CapacidadDto> esperado = List.of(new CapacidadDto("Mañana", 4, 8, 2, 10));
        when(repository.obtenerCapacidad(desde, hasta, "Mañana")).thenReturn(esperado);

        assertEquals(esperado, service.obtenerCapacidad(desde, hasta, "Mañana"));
        verify(repository).obtenerCapacidad(desde, hasta, "Mañana");
    }
}
