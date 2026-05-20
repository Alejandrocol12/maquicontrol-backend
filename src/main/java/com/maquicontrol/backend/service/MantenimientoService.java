package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Mantenimiento;
import com.maquicontrol.backend.repository.MantenimientoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MantenimientoService {

    @Autowired
    private MantenimientoRepository mantenimientoRepository;

    public List<Mantenimiento> obtenerTodos() {
        return mantenimientoRepository.findAll();
    }

    public Optional<Mantenimiento> obtenerPorId(Long id) {
        return mantenimientoRepository.findById(id);
    }

    public List<Mantenimiento> obtenerPorMaquina(String maquinaNombre) {
        return mantenimientoRepository.findByMaquinaNombre(maquinaNombre);
    }

    public Mantenimiento guardar(Mantenimiento mantenimiento) {
        return mantenimientoRepository.save(mantenimiento);
    }

    public Mantenimiento actualizar(Long id, Mantenimiento mantenimientoActualizado) {
        Mantenimiento mantenimiento = mantenimientoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Mantenimiento no encontrado"));
        mantenimiento.setMaquinaNombre(mantenimientoActualizado.getMaquinaNombre());
        mantenimiento.setTipo(mantenimientoActualizado.getTipo());
        mantenimiento.setDescripcion(mantenimientoActualizado.getDescripcion());
        mantenimiento.setCosto(mantenimientoActualizado.getCosto());
        mantenimiento.setHorometro(mantenimientoActualizado.getHorometro());
        mantenimiento.setEstado(mantenimientoActualizado.getEstado());
        mantenimiento.setFecha(mantenimientoActualizado.getFecha());
        return mantenimientoRepository.save(mantenimiento);
    }

    public void eliminar(Long id) {
        mantenimientoRepository.deleteById(id);
    }
}