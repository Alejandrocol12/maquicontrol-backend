package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Salario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalarioRepository extends JpaRepository<Salario, Long> {
    List<Salario> findByUsuarioId(Long usuarioId);
    List<Salario> findByUsuarioIdAndOperadorNombre(Long usuarioId, String operadorNombre);
    List<Salario> findByOperadorNombre(String operadorNombre);
    void deleteByOperadorNombre(String operadorNombre);
    void deleteByMaquinaNombre(String maquinaNombre);
    List<Salario> findByFaenaId(Long faenaId);

    @Modifying
    @Query("UPDATE Salario s SET s.maquinaNombre = :nuevo WHERE s.usuarioId = :uid AND s.maquinaNombre = :viejo")
    void actualizarNombreMaquina(@Param("uid") Long uid, @Param("viejo") String viejo, @Param("nuevo") String nuevo);
}
