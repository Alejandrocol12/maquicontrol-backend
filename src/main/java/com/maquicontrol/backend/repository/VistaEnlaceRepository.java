package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.VistaEnlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VistaEnlaceRepository extends JpaRepository<VistaEnlace, Long> {
    List<VistaEnlace> findByTokenOrderByFechaDesc(String token);
}
