package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Novedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NovedadRepository extends JpaRepository<Novedad, Long> {
    List<Novedad> findByOperadorIdOrderByFechaDesc(Long operadorId);
    List<Novedad> findByUsuarioIdOrderByFechaDesc(Long usuarioId);
}
