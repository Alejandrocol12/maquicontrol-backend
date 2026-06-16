package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Salario;
import com.maquicontrol.backend.repository.SalarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SalarioService {

    @Autowired
    private SalarioRepository salarioRepository;

    public List<Salario> obtenerTodos(Long userId) {
        return salarioRepository.findByUsuarioId(userId);
    }

    public Optional<Salario> obtenerPorId(Long id) {
        return salarioRepository.findById(id);
    }

    public List<Salario> obtenerPorOperador(Long userId, String operadorNombre) {
        return salarioRepository.findByUsuarioIdAndOperadorNombre(userId, operadorNombre);
    }

    public Salario guardar(Long userId, Salario salario) {
        salario.setUsuarioId(userId);
        salario.setTotalBruto(salario.getHorasTrabajadas() * salario.getValorHora());
        salario.setTotalNeto(salario.getTotalBruto() - salario.getAnticipos());
        return salarioRepository.save(salario);
    }

    public Salario actualizar(Long id, Salario salarioActualizado) {
        Salario salario = salarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Salario no encontrado"));
        salario.setOperadorNombre(salarioActualizado.getOperadorNombre());
        salario.setMaquinaNombre(salarioActualizado.getMaquinaNombre());
        salario.setHorasTrabajadas(salarioActualizado.getHorasTrabajadas());
        salario.setValorHora(salarioActualizado.getValorHora());
        salario.setAnticipos(salarioActualizado.getAnticipos());
        salario.setTotalBruto(salarioActualizado.getHorasTrabajadas() * salarioActualizado.getValorHora());
        salario.setTotalNeto(salario.getTotalBruto() - salarioActualizado.getAnticipos());
        salario.setEstado(salarioActualizado.getEstado());
        salario.setFecha(salarioActualizado.getFecha());
        return salarioRepository.save(salario);
    }

    public void eliminar(Long id) {
        salarioRepository.deleteById(id);
    }
}