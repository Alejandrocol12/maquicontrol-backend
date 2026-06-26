package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Combustible;
import com.maquicontrol.backend.model.Gasto;
import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.repository.CombustibleRepository;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.GastoRepository;
import com.maquicontrol.backend.repository.MaquinaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CombustibleService {

    @Autowired private CombustibleRepository combustibleRepository;
    @Autowired private GastoRepository gastoRepository;
    @Autowired private FaenaRepository faenaRepository;
    @Autowired private MaquinaRepository maquinaRepository;

    public List<Combustible> obtenerTodos(Long userId) {
        return combustibleRepository.findByUsuarioId(userId);
    }

    public Optional<Combustible> obtenerPorId(Long id) {
        return combustibleRepository.findById(id);
    }

    public List<Combustible> obtenerPorMaquina(Long userId, String maquinaNombre) {
        return combustibleRepository.findByUsuarioIdAndMaquinaNombre(userId, maquinaNombre);
    }

    @Transactional
    public Combustible guardar(Long userId, Combustible combustible) {
        combustible.setUsuarioId(userId);
        combustible.setTotal(combustible.getGalones() * combustible.getPrecioPorGalon());

        Long faenaId = null;
        if (combustible.getMaquinaNombre() != null) {
            faenaId = faenaRepository
                .findByUsuarioIdAndMaquinaNombreAndEstado(userId, combustible.getMaquinaNombre(), "activa")
                .map(f -> f.getId()).orElse(null);
        }
        if (faenaId != null) combustible.setFaenaId(faenaId);

        // #10: actualizar horómetro de la máquina si el registrado al cargar es mayor
        if (combustible.getHorometroAlCargar() > 0 && combustible.getMaquinaNombre() != null) {
            maquinaRepository.findByUsuarioIdAndNombre(userId, combustible.getMaquinaNombre())
                .ifPresent(maq -> {
                    if (combustible.getHorometroAlCargar() > maq.getHorometroActual()) {
                        maq.setHorometroActual(combustible.getHorometroAlCargar());
                        maquinaRepository.save(maq);
                    }
                });
        }

        Combustible saved = combustibleRepository.save(combustible);

        // #4: crear Gasto vinculado con descripción estándar para identificarlo
        Gasto gasto = new Gasto();
        gasto.setUsuarioId(userId);
        gasto.setDescripcion("Combustible — " + combustible.getMaquinaNombre()
            + " (" + combustible.getGalones() + " gal)");
        gasto.setCategoria("Combustible");
        gasto.setMonto(combustible.getTotal());
        gasto.setFecha(combustible.getFecha());
        gasto.setMaquinaNombre(combustible.getMaquinaNombre());
        if (faenaId != null) gasto.setFaenaId(faenaId);
        Gasto gastoSaved = gastoRepository.save(gasto);

        // Guardar referencia cruzada
        saved.setGastoGeneradoId(gastoSaved.getId());
        return combustibleRepository.save(saved);
    }

    @Transactional
    public void eliminar(Long id) {
        combustibleRepository.findById(id).ifPresent(c -> {
            if (c.getGastoGeneradoId() != null)
                gastoRepository.deleteById(c.getGastoGeneradoId());
            combustibleRepository.deleteById(id);
        });
    }
}
