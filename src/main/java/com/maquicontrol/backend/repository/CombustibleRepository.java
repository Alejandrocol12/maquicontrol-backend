package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Combustible;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CombustibleRepository extends JpaRepository<Combustible, Long> {
    List<Combustible> findByUsuarioId(Long usuarioId);
    List<Combustible> findByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);
    List<Combustible> findByMaquinaNombre(String maquinaNombre);
    void deleteByMaquinaNombre(String maquinaNombre);
    void deleteByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);
}