package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.SesionUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface SesionUsuarioRepository extends JpaRepository<SesionUsuario, String> {
    List<SesionUsuario> findByUsuarioIdAndRevocadaFalseAndCreadaAfterOrderByUltimaActividadDesc(Long usuarioId, LocalDateTime desde);

    // Las sesiones más viejas que el token ya no sirven: se borran
    @Transactional
    @Modifying
    @Query("delete from SesionUsuario s where s.creada < :limite")
    void borrarAnterioresA(@Param("limite") LocalDateTime limite);
}
