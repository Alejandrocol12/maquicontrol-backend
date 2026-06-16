package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Combustible;
import com.maquicontrol.backend.model.Gasto;
import com.maquicontrol.backend.repository.CombustibleRepository;
import com.maquicontrol.backend.repository.GastoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CombustibleService {

    @Autowired
    private CombustibleRepository combustibleRepository;

    @Autowired
    private GastoRepository gastoRepository;

    public List<Combustible> obtenerTodos(Long userId) {
        return combustibleRepository.findByUsuarioId(userId);
    }

    public Optional<Combustible> obtenerPorId(Long id) {
        return combustibleRepository.findById(id);
    }

    public List<Combustible> obtenerPorMaquina(Long userId, String maquinaNombre) {
        return combustibleRepository.findByUsuarioIdAndMaquinaNombre(userId, maquinaNombre);
    }

    public Combustible guardar(Long userId, Combustible combustible) {
        combustible.setUsuarioId(userId);
        combustible.setTotal(combustible.getGalones() * combustible.getPrecioPorGalon());
        Combustible saved = combustibleRepository.save(combustible);

        // Agregar automáticamente como gasto del mismo usuario
        Gasto gasto = new Gasto();
        gasto.setUsuarioId(userId);
        gasto.setDescripcion("Combustible " + combustible.getGalones() + " galones");
        gasto.setCategoria("Combustible");
        gasto.setMonto(combustible.getTotal());
        gasto.setFecha(combustible.getFecha());
        gasto.setMaquinaNombre(combustible.getMaquinaNombre());
        gastoRepository.save(gasto);

        return saved;
    }

    public void eliminar(Long id) {
        combustibleRepository.deleteById(id);
    }
}