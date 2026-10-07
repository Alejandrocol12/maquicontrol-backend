package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Corte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CorteRepository extends JpaRepository<Corte, Long> {
    List<Corte> findByUsuarioId(Long usuarioId);
    List<Corte> findByFaenaId(Long faenaId);
}
