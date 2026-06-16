package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Gasto;
import com.maquicontrol.backend.repository.GastoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GastoService {

    @Autowired
    private GastoRepository gastoRepository;

    public List<Gasto> obtenerTodos(Long userId) {
        return gastoRepository.findByUsuarioId(userId);
    }

    public Optional<Gasto> obtenerPorId(Long id) {
        return gastoRepository.findById(id);
    }

    public List<Gasto> obtenerPorMaquina(Long userId, String maquinaNombre) {
        return gastoRepository.findByUsuarioIdAndMaquinaNombre(userId, maquinaNombre);
    }

    public Gasto guardar(Long userId, Gasto gasto) {
        gasto.setUsuarioId(userId);
        return gastoRepository.save(gasto);
    }

    public Gasto actualizar(Long id, Gasto gastoActualizado) {
        Gasto gasto = gastoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));
        gasto.setDescripcion(gastoActualizado.getDescripcion());
        gasto.setCategoria(gastoActualizado.getCategoria());
        gasto.setMonto(gastoActualizado.getMonto());
        gasto.setFecha(gastoActualizado.getFecha());
        gasto.setMaquinaNombre(gastoActualizado.getMaquinaNombre());
        return gastoRepository.save(gasto);
    }

    public void eliminar(Long id) {
        gastoRepository.deleteById(id);
    }
}
