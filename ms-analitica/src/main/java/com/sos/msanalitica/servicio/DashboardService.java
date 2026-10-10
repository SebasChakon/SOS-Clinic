package com.sos.msanalitica.servicio;

import com.sos.msanalitica.dto.CapacidadDto;
import com.sos.msanalitica.dto.LlegadasDto;
import com.sos.msanalitica.dto.ResumenDto;
import com.sos.msanalitica.repositorio.DashboardRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {

    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    public ResumenDto obtenerResumen(LocalDate desde, LocalDate hasta, String localidad, String turno) {
        return repository.obtenerResumen(desde, hasta, localidad, turno);
    }

    public LlegadasDto obtenerLlegadas(LocalDate desde, LocalDate hasta, String localidad, String turno) {
        return new LlegadasDto(
                repository.obtenerPorHora(desde, hasta, localidad, turno),
                repository.obtenerPorPrioridad(desde, hasta, localidad, turno),
                repository.obtenerPorLocalidad(desde, hasta, localidad, turno));
    }

    public List<CapacidadDto> obtenerCapacidad(LocalDate desde, LocalDate hasta, String turno) {
        return repository.obtenerCapacidad(desde, hasta, turno);
    }
}
