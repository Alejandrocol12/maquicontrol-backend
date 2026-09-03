package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Ingreso;
import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.IngresoRepository;
import com.maquicontrol.backend.repository.MaquinaRepository;
import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class IngresoService {

    @Autowired private IngresoRepository ingresoRepository;
    @Autowired private FaenaRepository faenaRepository;
    @Autowired private MaquinaRepository maquinaRepository;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private OperadorRepository operadorRepo;
    @Autowired private FaenaService faenaService;

    // Si userId pertenece a un operador, devuelve el userId del admin dueño
    private Long resolverAdminId(Long userId) {
        if (userId == null) return null;
        return usuarioRepo.findById(userId)
            .filter(u -> u.getOperadorId() != null)
            .flatMap(u -> operadorRepo.findById(u.getOperadorId()))
            .map(op -> op.getUsuarioId())
            .orElse(userId);
    }

    public List<Ingreso> obtenerTodos(Long userId) {
        return ingresoRepository.findByUsuarioId(userId);
    }

    public Optional<Ingreso> obtenerPorId(Long id) {
        return ingresoRepository.findById(id);
    }

    public List<Ingreso> obtenerPorMaquina(Long userId, String maquinaNombre) {
        return ingresoRepository.findByUsuarioIdAndMaquinaNombre(userId, maquinaNombre);
    }

    @Transactional
    public Ingreso guardar(Long userId, Ingreso ingreso) {
        Long adminId = resolverAdminId(userId);
        ingreso.setUsuarioId(adminId);
        ingreso.setTotal(ingreso.getCantidad() * ingreso.getValorUnitario());
        if (ingreso.getMaquinaNombre() != null && ingreso.getFaenaId() == null) {
            faenaRepository.findByUsuarioIdAndMaquinaNombreAndEstado(adminId, ingreso.getMaquinaNombre(), "activa")
                .ifPresent(f -> ingreso.setFaenaId(f.getId()));
        }
        Ingreso saved = ingresoRepository.save(ingreso);
        faenaService.recalcularTotalesSiCerrada(saved.getFaenaId());
        return saved;
    }

    @Transactional
    public Ingreso actualizar(Long id, Ingreso ingresoActualizado) {
        Ingreso ingreso = ingresoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Ingreso no encontrado"));
        ingreso.setDescripcion(ingresoActualizado.getDescripcion());
        ingreso.setTipoTrabajo(ingresoActualizado.getTipoTrabajo());
        ingreso.setCantidad(ingresoActualizado.getCantidad());
        ingreso.setValorUnitario(ingresoActualizado.getValorUnitario());
        ingreso.setTotal(ingresoActualizado.getCantidad() * ingresoActualizado.getValorUnitario());
        ingreso.setFecha(ingresoActualizado.getFecha());
        ingreso.setMaquinaNombre(ingresoActualizado.getMaquinaNombre());
        Ingreso saved = ingresoRepository.save(ingreso);
        faenaService.recalcularTotalesSiCerrada(saved.getFaenaId());
        return saved;
    }

    @Transactional
    public void eliminar(Long id) {
        Ingreso ingreso = ingresoRepository.findById(id).orElse(null);
        if (ingreso != null && "Horas".equals(ingreso.getTipoTrabajo()) && ingreso.getMaquinaNombre() != null) {
            maquinaRepository.findByUsuarioIdAndNombre(ingreso.getUsuarioId(), ingreso.getMaquinaNombre())
                .ifPresent(maq -> {
                    maq.setHorometroActual(maq.getHorometroActual() - ingreso.getCantidad());
                    maquinaRepository.save(maq);
                });
        }
        ingresoRepository.deleteById(id);
        if (ingreso != null) faenaService.recalcularTotalesSiCerrada(ingreso.getFaenaId());
    }
}
