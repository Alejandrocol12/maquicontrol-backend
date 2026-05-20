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

    public List<Combustible> obtenerTodos() {
        return combustibleRepository.findAll();
    }

    public Optional<Combustible> obtenerPorId(Long id) {
        return combustibleRepository.findById(id);
    }

    public List<Combustible> obtenerPorMaquina(String maquinaNombre) {
        return combustibleRepository.findByMaquinaNombre(maquinaNombre);
    }

    public Combustible guardar(Combustible combustible) {
        // Calcular total automáticamente
        combustible.setTotal(combustible.getGalones() * combustible.getPrecioPorGalon());
        Combustible saved = combustibleRepository.save(combustible);

        // Agregar automáticamente como gasto
        Gasto gasto = new Gasto();
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