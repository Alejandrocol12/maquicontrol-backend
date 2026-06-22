package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Mantenimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {
    List<Mantenimiento> findByUsuarioId(Long usuarioId);
    List<Mantenimiento> findByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);
    List<Mantenimiento> findByMaquinaNombre(String maquinaNombre);
    void deleteByMaquinaNombre(String maquinaNombre);
    void deleteByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);
    List<Mantenimiento> findByFaenaId(Long faenaId);

    @Modifying
    @Query("UPDATE Mantenimiento m SET m.maquinaNombre = :nuevo WHERE m.usuarioId = :uid AND m.maquinaNombre = :viejo")
    void actualizarNombreMaquina(@Param("uid") Long uid, @Param("viejo") String viejo, @Param("nuevo") String nuevo);
}
