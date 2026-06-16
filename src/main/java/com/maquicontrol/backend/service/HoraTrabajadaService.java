package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.HoraTrabajada;
import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.repository.HoraTrabajadaRepository;
import com.maquicontrol.backend.repository.MaquinaRepository;
import com.maquicontrol.backend.repository.OperadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class HoraTrabajadaService {

    @Autowired
    private HoraTrabajadaRepository horaRepository;

    @Autowired
    private MaquinaRepository maquinaRepository;

    @Autowired
    private OperadorRepository operadorRepository;

    public List<HoraTrabajada> obtenerTodas(Long userId) {
        return horaRepository.findByUsuarioId(userId);
    }

    public Optional<HoraTrabajada> obtenerPorId(Long id) {
        return horaRepository.findById(id);
    }

    public List<HoraTrabajada> obtenerPorOperadorId(Long userId, Long operadorId) {
        String nombre = operadorRepository.findById(operadorId)
                .map(o -> o.getNombre())
                .orElse("");
        return horaRepository.findByUsuarioIdAndOperadorIdOrNombre(userId, operadorId, nombre);
    }

    public List<HoraTrabajada> obtenerPorOperador(String operadorNombre) {
        return horaRepository.findByOperadorNombre(operadorNombre);
    }

    public List<HoraTrabajada> obtenerPorMaquina(Long userId, String maquinaNombre) {
        return horaRepository.findByUsuarioIdAndMaquinaNombre(userId, maquinaNombre);
    }

public HoraTrabajada guardar(Long userId, HoraTrabajada hora) {
    hora.setUsuarioId(userId);
    hora.setHorometroFin(hora.getHorometroInicio() + hora.getHoras());

    HoraTrabajada saved = horaRepository.save(hora);

    // Actualizar horómetro de la máquina automáticamente
    maquinaRepository.findByUsuarioId(userId).stream()
        .filter(m -> m.getNombre().equals(hora.getMaquinaNombre()))
        .findFirst()
        .ifPresent(m -> {
            m.setHorometroActual((int) hora.getHorometroFin());
            maquinaRepository.save(m);
        });

    return saved;
}

    public void eliminar(Long id) {
        horaRepository.deleteById(id);
    }
}
