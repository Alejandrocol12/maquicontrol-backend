package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Novedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NovedadRepository extends JpaRepository<Novedad, Long> {
    List<Novedad> findByOperadorIdOrderByFechaDesc(Long operadorId);
    List<Novedad> findByUsuarioIdOrderByFechaDesc(Long usuarioId);

    @Modifying
    @Query("UPDATE Novedad n SET n.operadorNombre = :nuevo WHERE n.usuarioId = :uid AND n.operadorNombre = :viejo")
    void actualizarNombreOperador(@Param("uid") Long uid, @Param("viejo") String viejo, @Param("nuevo") String nuevo);
}
