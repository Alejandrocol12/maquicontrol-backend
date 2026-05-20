package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.HoraTrabajada;
import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.repository.HoraTrabajadaRepository;
import com.maquicontrol.backend.repository.MaquinaRepository;
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

    public List<HoraTrabajada> obtenerTodas() {
        return horaRepository.findAll();
    }

    public Optional<HoraTrabajada> obtenerPorId(Long id) {
        return horaRepository.findById(id);
    }

    public List<HoraTrabajada> obtenerPorOperador(String operadorNombre) {
        return horaRepository.findByOperadorNombre(operadorNombre);
    }

    public List<HoraTrabajada> obtenerPorMaquina(String maquinaNombre) {
        return horaRepository.findByMaquinaNombre(maquinaNombre);
    }

public HoraTrabajada guardar(HoraTrabajada hora) {
    // Calcular horómetro fin ANTES de guardar
    hora.setHorometroFin(hora.getHorometroInicio() + hora.getHoras());

    HoraTrabajada saved = horaRepository.save(hora);

    // Actualizar horómetro de la máquina automáticamente
    List<Maquina> maquinas = maquinaRepository.findAll();
    maquinas.stream()
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
