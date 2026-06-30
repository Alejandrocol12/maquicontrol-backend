package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Periodo;
import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.repository.PeriodoRepository;
import com.maquicontrol.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PeriodoService {

    @Autowired private PeriodoRepository periodoRepository;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private OperadorRepository operadorRepo;

    private Long resolverAdminId(Long userId) {
        if (userId == null) return null;
        return usuarioRepo.findById(userId)
            .filter(u -> u.getOperadorId() != null)
            .flatMap(u -> operadorRepo.findById(u.getOperadorId()))
            .map(op -> op.getUsuarioId())
            .orElse(userId);
    }

    public List<Periodo> obtenerPorOperador(Long userId, Long operadorId) {
        return periodoRepository.findByUsuarioIdAndOperadorId(resolverAdminId(userId), operadorId);
    }

    public Optional<Periodo> obtenerActivo(Long userId, Long operadorId) {
        return periodoRepository.findByUsuarioIdAndOperadorIdAndEstado(resolverAdminId(userId), operadorId, "activo");
    }

    public Periodo crear(Long userId, Long operadorId, Periodo periodo) {
        periodo.setUsuarioId(resolverAdminId(userId));
        periodo.setOperadorId(operadorId);
        return periodoRepository.save(periodo);
    }

    public void eliminar(Long id) {
        periodoRepository.deleteById(id);
    }

    public Periodo actualizar(Long id, Periodo datos) {
        Periodo p = periodoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Periodo no encontrado"));
        if (datos.getEstado() != null) p.setEstado(datos.getEstado());
        if (datos.getFechaFin() != null) p.setFechaFin(datos.getFechaFin());
        if (datos.getHorasTotal() != 0) p.setHorasTotal(datos.getHorasTotal());
        if (datos.getSalarioBruto() != 0) p.setSalarioBruto(datos.getSalarioBruto());
        if (datos.getSalarioNeto() != 0) p.setSalarioNeto(datos.getSalarioNeto());
        if (datos.getNota() != null) p.setNota(datos.getNota());
        if (datos.getDesdeHoraId() != null) p.setDesdeHoraId(datos.getDesdeHoraId());
        p.setAnticipos(datos.getAnticipos());
        return periodoRepository.save(p);
    }
}
