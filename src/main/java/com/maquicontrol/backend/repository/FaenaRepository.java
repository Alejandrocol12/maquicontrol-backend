package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Faena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaenaRepository extends JpaRepository<Faena, Long> {
    List<Faena> findByUsuarioId(Long usuarioId);
    Optional<Faena> findByUsuarioIdAndMaquinaNombreAndEstado(Long usuarioId, String maquinaNombre, String estado);
    List<Faena> findByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);

    @Modifying
    @Query("UPDATE Faena f SET f.maquinaNombre = :nuevo WHERE f.usuarioId = :uid AND f.maquinaNombre = :viejo")
    void actualizarNombreMaquina(@Param("uid") Long uid, @Param("viejo") String viejo, @Param("nuevo") String nuevo);
}
