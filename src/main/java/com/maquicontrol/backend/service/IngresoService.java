package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Ingreso;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.IngresoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class IngresoService {

    @Autowired
    private IngresoRepository ingresoRepository;

    @Autowired
    private FaenaRepository faenaRepository;

    public List<Ingreso> obtenerTodos(Long userId) {
        return ingresoRepository.findByUsuarioId(userId);
    }

    public Optional<Ingreso> obtenerPorId(Long id) {
        return ingresoRepository.findById(id);
    }

    public List<Ingreso> obtenerPorMaquina(Long userId, String maquinaNombre) {
        return ingresoRepository.findByUsuarioIdAndMaquinaNombre(userId, maquinaNombre);
    }

    public Ingreso guardar(Long userId, Ingreso ingreso) {
        ingreso.setUsuarioId(userId);
        ingreso.setTotal(ingreso.getCantidad() * ingreso.getValorUnitario());
        if (ingreso.getMaquinaNombre() != null && ingreso.getFaenaId() == null) {
            faenaRepository.findByUsuarioIdAndMaquinaNombreAndEstado(userId, ingreso.getMaquinaNombre(), "activa")
                .ifPresent(f -> ingreso.setFaenaId(f.getId()));
        }
        return ingresoRepository.save(ingreso);
    }

    public Ingreso actualizar(Long id, Ingreso ingresoActualizado) {
        Ingreso ingreso = ingresoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Ingreso no encontrado"));
        ingreso.setDescripcion(ingresoActualizado.getDescripcion());
        ingreso.setTipoTrabajo(ingresoActualizado.getTipoTrabajo());
        ingreso.setCantidad(ingresoActualizado.getCantidad());
        ingreso.setValorUnitario(ingresoActualizado.getValorUnitario());
        ingreso.setTotal(ingresoActualizado.getCantidad() * ingresoActualizado.getValorUnitario());
        ingreso.setFecha(ingresoActualizado.getFecha());
        ingreso.setMaquinaNombre(ingresoActualizado.getMaquinaNombre());
        return ingresoRepository.save(ingreso);
    }

    public void eliminar(Long id) {
        ingresoRepository.deleteById(id);
    }
}
