package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.HoraTrabajada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HoraTrabajadaRepository extends JpaRepository<HoraTrabajada, Long> {
    List<HoraTrabajada> findByUsuarioId(Long usuarioId);
    List<HoraTrabajada> findByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);
    void deleteByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);
    List<HoraTrabajada> findByOperadorId(Long operadorId);
    List<HoraTrabajada> findByOperadorNombre(String operadorNombre);
    List<HoraTrabajada> findByMaquinaNombre(String maquinaNombre);
    void deleteByOperadorNombre(String operadorNombre);
    void deleteByMaquinaNombre(String maquinaNombre);

    @Query("SELECT h FROM HoraTrabajada h WHERE h.usuarioId = :uid AND (h.operadorId = :id OR (h.operadorId IS NULL AND h.operadorNombre = :nombre)) ORDER BY h.fecha DESC")
    List<HoraTrabajada> findByUsuarioIdAndOperadorIdOrNombre(@Param("uid") Long uid, @Param("id") Long id, @Param("nombre") String nombre);

    @Query("SELECT h FROM HoraTrabajada h WHERE h.operadorId = :id OR (h.operadorId IS NULL AND h.operadorNombre = :nombre) ORDER BY h.fecha DESC")
    List<HoraTrabajada> findByOperadorIdOrNombre(@Param("id") Long id, @Param("nombre") String nombre);
}