package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.HoraTrabajada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HoraTrabajadaRepository extends JpaRepository<HoraTrabajada, Long> {
    List<HoraTrabajada> findByOperadorNombre(String operadorNombre);
    List<HoraTrabajada> findByMaquinaNombre(String maquinaNombre);
    void deleteByOperadorNombre(String operadorNombre);
    void deleteByMaquinaNombre(String maquinaNombre);
}