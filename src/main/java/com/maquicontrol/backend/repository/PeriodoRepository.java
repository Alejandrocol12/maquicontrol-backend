package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Periodo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PeriodoRepository extends JpaRepository<Periodo, Long> {
    List<Periodo> findByUsuarioIdAndOperadorId(Long usuarioId, Long operadorId);
    Optional<Periodo> findByUsuarioIdAndOperadorIdAndEstado(Long usuarioId, Long operadorId, String estado);
    List<Periodo> findByOperadorId(Long operadorId);
    Optional<Periodo> findByOperadorIdAndEstado(Long operadorId, String estado);
}
