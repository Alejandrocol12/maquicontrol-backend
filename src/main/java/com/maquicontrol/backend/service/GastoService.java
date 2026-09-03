package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Gasto;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.GastoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GastoService {

    @Autowired private GastoRepository gastoRepository;
    @Autowired private FaenaRepository faenaRepository;
    @Autowired private FaenaService faenaService;

    public List<Gasto> obtenerTodos(Long userId) {
        return gastoRepository.findByUsuarioId(userId);
    }

    public Optional<Gasto> obtenerPorId(Long id) {
        return gastoRepository.findById(id);
    }

    public List<Gasto> obtenerPorMaquina(Long userId, String maquinaNombre) {
        return gastoRepository.findByUsuarioIdAndMaquinaNombre(userId, maquinaNombre);
    }

    @Transactional
    public Gasto guardar(Long userId, Gasto gasto) {
        gasto.setUsuarioId(userId);
        if (gasto.getMaquinaNombre() != null && gasto.getFaenaId() == null) {
            faenaRepository.findByUsuarioIdAndMaquinaNombreAndEstado(userId, gasto.getMaquinaNombre(), "activa")
                .ifPresent(f -> gasto.setFaenaId(f.getId()));
        }
        Gasto saved = gastoRepository.save(gasto);
        faenaService.recalcularTotalesSiCerrada(saved.getFaenaId());
        return saved;
    }

    @Transactional
    public Gasto actualizar(Long id, Gasto gastoActualizado) {
        Gasto gasto = gastoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));
        gasto.setDescripcion(gastoActualizado.getDescripcion());
        gasto.setCategoria(gastoActualizado.getCategoria());
        gasto.setMonto(gastoActualizado.getMonto());
        gasto.setFecha(gastoActualizado.getFecha());
        gasto.setMaquinaNombre(gastoActualizado.getMaquinaNombre());
        Gasto saved = gastoRepository.save(gasto);
        faenaService.recalcularTotalesSiCerrada(saved.getFaenaId());
        return saved;
    }

    @Transactional
    public void eliminar(Long id) {
        Gasto gasto = gastoRepository.findById(id).orElse(null);
        gastoRepository.deleteById(id);
        if (gasto != null) faenaService.recalcularTotalesSiCerrada(gasto.getFaenaId());
    }

    public void guardarFactura(Long gastoId, String nombre, byte[] data) {
        Gasto gasto = gastoRepository.findById(gastoId)
            .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));
        gasto.setFacturaNombre(nombre);
        gasto.setFacturaPdf(data);
        gastoRepository.save(gasto);
    }

    public void eliminarFactura(Long gastoId) {
        Gasto gasto = gastoRepository.findById(gastoId)
            .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));
        gasto.setFacturaNombre(null);
        gasto.setFacturaPdf(null);
        gastoRepository.save(gasto);
    }
}
