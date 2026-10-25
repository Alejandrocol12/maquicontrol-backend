package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.LecturaHorometro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LecturaHorometroRepository extends JpaRepository<LecturaHorometro, Long> {

    // La lectura que el operador tiene a medias (si la hay)
    Optional<LecturaHorometro> findFirstByOperadorIdAndEstadoInOrderByIdDesc(Long operadorId, Collection<String> estados);

    List<LecturaHorometro> findByOperadorIdAndEstadoIn(Long operadorId, Collection<String> estados);

    List<LecturaHorometro> findByUsuarioIdAndEstadoOrderByIdDesc(Long usuarioId, String estado);

    // Retención: se borra solo la foto, el registro de la lectura se conserva
    @Transactional
    @Modifying
    @Query("update LecturaHorometro l set l.foto = null where l.creada < :limite and l.foto is not null")
    int purgarFotosAnterioresA(@Param("limite") LocalDateTime limite);

    // Lecturas que el operador dejó a medias y nunca terminó
    @Transactional
    @Modifying
    @Query("update LecturaHorometro l set l.estado = 'CANCELADA', l.resuelta = :ahora where l.creada < :limite and l.estado in ('ELEGIR_MAQUINA','CONFIRMAR','ESPERA_NUMERO')")
    int cancelarAbandonadas(@Param("limite") LocalDateTime limite, @Param("ahora") LocalDateTime ahora);
}
