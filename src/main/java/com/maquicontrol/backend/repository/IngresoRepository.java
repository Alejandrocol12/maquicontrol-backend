package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Ingreso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngresoRepository extends JpaRepository<Ingreso, Long> {
    List<Ingreso> findByUsuarioId(Long usuarioId);
    List<Ingreso> findByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);
    List<Ingreso> findByMaquinaNombre(String maquinaNombre);
    void deleteByMaquinaNombre(String maquinaNombre);
    void deleteByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);
    List<Ingreso> findByFaenaId(Long faenaId);

    @Modifying
    @Query("UPDATE Ingreso i SET i.maquinaNombre = :nuevo WHERE i.usuarioId = :uid AND i.maquinaNombre = :viejo")
    void actualizarNombreMaquina(@Param("uid") Long uid, @Param("viejo") String viejo, @Param("nuevo") String nuevo);
}
