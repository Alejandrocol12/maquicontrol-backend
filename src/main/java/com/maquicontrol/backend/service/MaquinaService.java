package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MaquinaService {

    @Autowired private MaquinaRepository maquinaRepository;
    @Autowired private IngresoRepository ingresoRepository;
    @Autowired private GastoRepository gastoRepository;
    @Autowired private CombustibleRepository combustibleRepository;
    @Autowired private MantenimientoRepository mantenimientoRepository;
    @Autowired private HoraTrabajadaRepository horaRepository;

    // Obtener todas las máquinas
    public List<Maquina> obtenerTodas(Long userId) {
        return maquinaRepository.findByUsuarioId(userId);
    }

    // Obtener una máquina por ID
    public Optional<Maquina> obtenerPorId(Long id) {
        return maquinaRepository.findById(id);
    }

    // Registrar nueva máquina
    public Maquina guardar(Long userId, Maquina maquina) {
        maquina.setUsuarioId(userId);
        return maquinaRepository.save(maquina);
    }

    // Actualizar máquina existente
    public Maquina actualizar(Long id, Maquina maquinaActualizada) {
        Maquina maquina = maquinaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Máquina no encontrada"));
        maquina.setNombre(maquinaActualizada.getNombre());
        maquina.setTipo(maquinaActualizada.getTipo());
        maquina.setPlaca(maquinaActualizada.getPlaca());
        maquina.setHorometroActual(maquinaActualizada.getHorometroActual());
        maquina.setEstado(maquinaActualizada.getEstado());
        maquina.setOperadorNombre(maquinaActualizada.getOperadorNombre());
        maquina.setValorHoraOperador(maquinaActualizada.getValorHoraOperador());
        maquina.setValorHoraMaquina(maquinaActualizada.getValorHoraMaquina());
        return maquinaRepository.save(maquina);
    }

    // Eliminar máquina y todos sus datos asociados
    @Transactional
    public void eliminar(Long id, Long userId) {
        maquinaRepository.findById(id).ifPresent(maq -> {
            String nombre = maq.getNombre();
            ingresoRepository.deleteByUsuarioIdAndMaquinaNombre(userId, nombre);
            gastoRepository.deleteByUsuarioIdAndMaquinaNombre(userId, nombre);
            combustibleRepository.deleteByUsuarioIdAndMaquinaNombre(userId, nombre);
            mantenimientoRepository.deleteByUsuarioIdAndMaquinaNombre(userId, nombre);
            horaRepository.deleteByUsuarioIdAndMaquinaNombre(userId, nombre);
            maquinaRepository.deleteById(id);
        });
    }
}