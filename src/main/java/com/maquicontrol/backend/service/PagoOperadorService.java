package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.PagoOperador;
import com.maquicontrol.backend.repository.PagoOperadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PagoOperadorService {

    @Autowired
    private PagoOperadorRepository pagoRepository;

    public List<PagoOperador> obtenerTodos(Long userId) {
        return pagoRepository.findByUsuarioId(userId);
    }

    public Optional<PagoOperador> obtenerPorId(Long id) {
        return pagoRepository.findById(id);
    }

    public List<PagoOperador> obtenerPorOperador(Long userId, String operadorNombre) {
        return pagoRepository.findByUsuarioIdAndOperadorNombre(userId, operadorNombre);
    }

    public PagoOperador guardar(Long userId, PagoOperador pago) {
        pago.setUsuarioId(userId);
        return pagoRepository.save(pago);
    }

    public PagoOperador actualizar(Long id, PagoOperador pagoActualizado) {
        PagoOperador pago = pagoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Pago no encontrado"));
        pago.setOperadorNombre(pagoActualizado.getOperadorNombre());
        pago.setDescripcion(pagoActualizado.getDescripcion());
        pago.setMonto(pagoActualizado.getMonto());
        pago.setFecha(pagoActualizado.getFecha());
        return pagoRepository.save(pago);
    }

    public void eliminar(Long id) {
        pagoRepository.deleteById(id);
    }
}
