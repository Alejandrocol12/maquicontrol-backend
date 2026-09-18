package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Maquina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaquinaRepository extends JpaRepository<Maquina, Long> {
    List<Maquina> findByUsuarioId(Long usuarioId);
    Optional<Maquina> findByUsuarioIdAndNombre(Long usuarioId, String nombre);
    List<Maquina> findByUsuarioIdAndOperadorNombre(Long usuarioId, String operadorNombre);

    @Modifying
    @Query("UPDATE Maquina m SET m.operadorNombre = :nuevo WHERE m.usuarioId = :uid AND m.operadorNombre = :viejo")
    void actualizarNombreOperador(@Param("uid") Long uid, @Param("viejo") String viejo, @Param("nuevo") String nuevo);
}
