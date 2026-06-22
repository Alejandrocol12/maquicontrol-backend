package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Operador;
import com.maquicontrol.backend.repository.HoraTrabajadaRepository;
import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.repository.PeriodoRepository;
import com.maquicontrol.backend.repository.SalarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class OperadorService {

    @Autowired private OperadorRepository operadorRepository;
    @Autowired private PeriodoRepository periodoRepository;
    @Autowired private HoraTrabajadaRepository horaRepository;
    @Autowired private SalarioRepository salarioRepository;

    public List<Operador> obtenerTodos(Long userId) {
        return operadorRepository.findByUsuarioId(userId);
    }

    public Optional<Operador> obtenerPorId(Long id) {
        return operadorRepository.findById(id);
    }

    public Operador guardar(Long userId, Operador operador) {
        operador.setUsuarioId(userId);
        return operadorRepository.save(operador);
    }

    public Operador actualizar(Long id, Operador datos) {
        Operador op = operadorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Operador no encontrado"));
        op.setNombre(datos.getNombre());
        op.setCedula(datos.getCedula());
        op.setTelefono(datos.getTelefono());
        op.setEmail(datos.getEmail());
        op.setObservaciones(datos.getObservaciones());
        op.setActivo(datos.isActivo());
        return operadorRepository.save(op);
    }

    @Transactional
    public void eliminar(Long id) {
        operadorRepository.findById(id).ifPresent(op -> {
            periodoRepository.deleteAll(periodoRepository.findByOperadorId(id));
            horaRepository.deleteByOperadorNombre(op.getNombre());
            salarioRepository.deleteByOperadorNombre(op.getNombre());
            operadorRepository.deleteById(id);
        });
    }
}
