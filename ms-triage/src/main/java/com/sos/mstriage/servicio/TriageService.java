/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.mstriage.servicio;

import com.sos.mstriage.dto.RegistroTriageRequest;
import com.sos.mstriage.modelo.CategoriaTriage;
import com.sos.mstriage.modelo.EstadoTriage;
import com.sos.mstriage.modelo.RegistroTriage;
import com.sos.mstriage.repositorio.CategoriaTriageRepository;
import com.sos.mstriage.repositorio.EstadoTriageRepository;
import com.sos.mstriage.repositorio.RegistroTriageRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TriageService {

    private final RegistroTriageRepository registroTriageRepository;
    private final CategoriaTriageRepository categoriaTriageRepository;
    private final EstadoTriageRepository estadoTriageRepository;

    public TriageService(RegistroTriageRepository registroTriageRepository,
                          CategoriaTriageRepository categoriaTriageRepository,
                          EstadoTriageRepository estadoTriageRepository) {
        this.registroTriageRepository = registroTriageRepository;
        this.categoriaTriageRepository = categoriaTriageRepository;
        this.estadoTriageRepository = estadoTriageRepository;
    }

    public RegistroTriage registrarLlegada(RegistroTriageRequest request) {
        CategoriaTriage categoria = categoriaTriageRepository.findById(request.getIdCategoriaTriage())
                .orElseThrow(() -> new NoSuchElementException("Categoria de triage no encontrada"));

        EstadoTriage estadoInicial = estadoTriageRepository.findByNombreEstado("En espera")
                .orElseThrow(() -> new NoSuchElementException("Estado 'En espera' no configurado en la base de datos"));

        RegistroTriage registro = new RegistroTriage();
        registro.setIdPaciente(request.getIdPaciente());
        registro.setCategoriaTriage(categoria);
        registro.setEstadoTriage(estadoInicial);
        registro.setUbicacion(request.getUbicacion());
        registro.setFechaHoraLlegada(LocalDateTime.now());
        registro.setIdEstado(1);

        return registroTriageRepository.save(registro);
    }

    public List<RegistroTriage> obtenerListaPriorizacion() {
        List<RegistroTriage> lista = registroTriageRepository.listaPriorizacion();

        // Calcula el tiempo de espera actual en minutos para cada paciente en la lista
        lista.forEach(r -> {
            long minutos = Duration.between(r.getFechaHoraLlegada(), LocalDateTime.now()).toMinutes();
            r.setTiempoEsperaMinutos((int) minutos);
        });

        return lista;
    }

    public RegistroTriage cambiarEstado(Integer idRegistro, String nombreNuevoEstado) {
        RegistroTriage registro = registroTriageRepository.findById(idRegistro)
                .orElseThrow(() -> new NoSuchElementException("Registro de triage no encontrado"));

        EstadoTriage nuevoEstado = estadoTriageRepository.findByNombreEstado(nombreNuevoEstado)
                .orElseThrow(() -> new NoSuchElementException("Estado no valido: " + nombreNuevoEstado));

        registro.setEstadoTriage(nuevoEstado);
        return registroTriageRepository.save(registro);
    }
}
