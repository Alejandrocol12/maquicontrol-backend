package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Operador;
import com.maquicontrol.backend.repository.OperadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OperadorService {

    @Autowired
    private OperadorRepository operadorRepository;

    public List<Operador> obtenerTodos() {
        return operadorRepository.findAll();
    }

    public Optional<Operador> obtenerPorId(Long id) {
        return operadorRepository.findById(id);
    }

    public Operador guardar(Operador operador) {
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

    public void eliminar(Long id) {
        operadorRepository.deleteById(id);
    }
}
