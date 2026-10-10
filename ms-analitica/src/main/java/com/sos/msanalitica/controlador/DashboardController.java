package com.sos.msanalitica.controlador;

import com.sos.msanalitica.dto.CapacidadDto;
import com.sos.msanalitica.dto.LlegadasDto;
import com.sos.msanalitica.dto.ResumenDto;
import com.sos.msanalitica.servicio.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumen")
    public ResumenDto resumen(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String localidad,
            @RequestParam(required = false) String turno) {
        validarRango(desde, hasta);
        return dashboardService.obtenerResumen(desde, hasta, localidad, turno);
    }

    @GetMapping("/llegadas")
    public LlegadasDto llegadas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String localidad,
            @RequestParam(required = false) String turno) {
        validarRango(desde, hasta);
        return dashboardService.obtenerLlegadas(desde, hasta, localidad, turno);
    }

    @GetMapping("/capacidad")
    public List<CapacidadDto> capacidad(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String turno) {
        validarRango(desde, hasta);
        return dashboardService.obtenerCapacidad(desde, hasta, turno);
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha inicial debe ser anterior o igual a la final.");
        }
    }
}
