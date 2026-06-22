package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Gasto;
import com.maquicontrol.backend.model.Mantenimiento;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.GastoRepository;
import com.maquicontrol.backend.repository.MantenimientoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MantenimientoService {

    @Autowired private MantenimientoRepository mantenimientoRepository;
    @Autowired private GastoRepository gastoRepository;
    @Autowired private FaenaRepository faenaRepository;

    public List<Mantenimiento> obtenerTodos(Long userId) {
        return mantenimientoRepository.findByUsuarioId(userId);
    }

    public Optional<Mantenimiento> obtenerPorId(Long id) {
        return mantenimientoRepository.findById(id);
    }

    public List<Mantenimiento> obtenerPorMaquina(Long userId, String maquinaNombre) {
        return mantenimientoRepository.findByUsuarioIdAndMaquinaNombre(userId, maquinaNombre);
    }

    @Transactional
    public Mantenimiento guardar(Long userId, Mantenimiento mantenimiento) {
        mantenimiento.setUsuarioId(userId);
        if (mantenimiento.getMaquinaNombre() != null && mantenimiento.getFaenaId() == null) {
            faenaRepository.findByUsuarioIdAndMaquinaNombreAndEstado(userId, mantenimiento.getMaquinaNombre(), "activa")
                .ifPresent(f -> mantenimiento.setFaenaId(f.getId()));
        }
        Mantenimiento saved = mantenimientoRepository.save(mantenimiento);

        // #3: crear Gasto automático si hay costo
        if (mantenimiento.getCosto() > 0) {
            Gasto gasto = new Gasto();
            gasto.setUsuarioId(userId);
            gasto.setDescripcion("Mantenimiento — " + mantenimiento.getTipo()
                + (mantenimiento.getDescripcion() != null && !mantenimiento.getDescripcion().isBlank()
                    ? ": " + mantenimiento.getDescripcion() : ""));
            gasto.setCategoria("Mantenimiento");
            gasto.setMonto(mantenimiento.getCosto());
            gasto.setFecha(mantenimiento.getFecha());
            gasto.setMaquinaNombre(mantenimiento.getMaquinaNombre());
            if (mantenimiento.getFaenaId() != null) gasto.setFaenaId(mantenimiento.getFaenaId());
            Gasto gastoSaved = gastoRepository.save(gasto);
            saved.setGastoGeneradoId(gastoSaved.getId());
            mantenimientoRepository.save(saved);
        }
        return saved;
    }

    @Transactional
    public Mantenimiento actualizar(Long id, Mantenimiento datos) {
        Mantenimiento m = mantenimientoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Mantenimiento no encontrado"));
        m.setMaquinaNombre(datos.getMaquinaNombre());
        m.setTipo(datos.getTipo());
        m.setDescripcion(datos.getDescripcion());
        m.setCosto(datos.getCosto());
        m.setHorometro(datos.getHorometro());
        m.setEstado(datos.getEstado());
        m.setFecha(datos.getFecha());

        // Actualizar el Gasto vinculado si existe
        if (m.getGastoGeneradoId() != null) {
            gastoRepository.findById(m.getGastoGeneradoId()).ifPresent(g -> {
                if (datos.getCosto() > 0) {
                    g.setMonto(datos.getCosto());
                    g.setFecha(datos.getFecha());
                    g.setMaquinaNombre(datos.getMaquinaNombre());
                    g.setDescripcion("Mantenimiento — " + datos.getTipo()
                        + (datos.getDescripcion() != null && !datos.getDescripcion().isBlank()
                            ? ": " + datos.getDescripcion() : ""));
                    gastoRepository.save(g);
                } else {
                    gastoRepository.deleteById(m.getGastoGeneradoId());
                    m.setGastoGeneradoId(null);
                }
            });
        }
        return mantenimientoRepository.save(m);
    }

    @Transactional
    public void eliminar(Long id) {
        mantenimientoRepository.findById(id).ifPresent(m -> {
            if (m.getGastoGeneradoId() != null) {
                gastoRepository.deleteById(m.getGastoGeneradoId());
            }
        });
        mantenimientoRepository.deleteById(id);
    }
}
