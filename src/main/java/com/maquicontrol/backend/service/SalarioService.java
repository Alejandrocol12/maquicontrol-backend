package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Gasto;
import com.maquicontrol.backend.model.Salario;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.GastoRepository;
import com.maquicontrol.backend.repository.SalarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SalarioService {

    @Autowired private SalarioRepository salarioRepository;
    @Autowired private GastoRepository gastoRepository;
    @Autowired private FaenaRepository faenaRepository;

    public List<Salario> obtenerTodos(Long userId) {
        return salarioRepository.findByUsuarioId(userId);
    }

    public Optional<Salario> obtenerPorId(Long id) {
        return salarioRepository.findById(id);
    }

    public List<Salario> obtenerPorOperador(Long userId, String operadorNombre) {
        return salarioRepository.findByUsuarioIdAndOperadorNombre(userId, operadorNombre);
    }

    @Transactional
    public Salario guardar(Long userId, Salario salario) {
        salario.setUsuarioId(userId);
        salario.setTotalBruto(salario.getHorasTrabajadas() * salario.getValorHora());
        salario.setTotalNeto(salario.getTotalBruto() - salario.getAnticipos());
        if (salario.getMaquinaNombre() != null && salario.getFaenaId() == null) {
            faenaRepository.findByUsuarioIdAndMaquinaNombreAndEstado(userId, salario.getMaquinaNombre(), "activa")
                .ifPresent(f -> salario.setFaenaId(f.getId()));
        }
        Salario saved = salarioRepository.save(salario);

        // #2: crear Gasto automático para incluir salario en el P&L
        Gasto gasto = new Gasto();
        gasto.setUsuarioId(userId);
        gasto.setDescripcion("Salario — " + salario.getOperadorNombre()
            + " (" + salario.getHorasTrabajadas() + " hrs)");
        gasto.setCategoria("Salario");
        gasto.setMonto(salario.getTotalNeto());
        gasto.setFecha(salario.getFecha());
        gasto.setMaquinaNombre(salario.getMaquinaNombre());
        if (salario.getFaenaId() != null) gasto.setFaenaId(salario.getFaenaId());
        Gasto gastoSaved = gastoRepository.save(gasto);

        saved.setGastoGeneradoId(gastoSaved.getId());
        return salarioRepository.save(saved);
    }

    @Transactional
    public Salario actualizar(Long id, Salario datos) {
        Salario salario = salarioRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Salario no encontrado"));
        salario.setOperadorNombre(datos.getOperadorNombre());
        salario.setMaquinaNombre(datos.getMaquinaNombre());
        salario.setHorasTrabajadas(datos.getHorasTrabajadas());
        salario.setValorHora(datos.getValorHora());
        salario.setAnticipos(datos.getAnticipos());
        salario.setTotalBruto(datos.getHorasTrabajadas() * datos.getValorHora());
        salario.setTotalNeto(salario.getTotalBruto() - datos.getAnticipos());
        salario.setEstado(datos.getEstado());
        salario.setFecha(datos.getFecha());

        // Actualizar el Gasto vinculado
        if (salario.getGastoGeneradoId() != null) {
            gastoRepository.findById(salario.getGastoGeneradoId()).ifPresent(g -> {
                g.setMonto(salario.getTotalNeto());
                g.setFecha(datos.getFecha());
                g.setMaquinaNombre(datos.getMaquinaNombre());
                g.setDescripcion("Salario — " + datos.getOperadorNombre()
                    + " (" + datos.getHorasTrabajadas() + " hrs)");
                gastoRepository.save(g);
            });
        }
        return salarioRepository.save(salario);
    }

    @Transactional
    public void eliminar(Long id) {
        salarioRepository.findById(id).ifPresent(s -> {
            if (s.getGastoGeneradoId() != null) {
                gastoRepository.deleteById(s.getGastoGeneradoId());
            }
        });
        salarioRepository.deleteById(id);
    }
}
