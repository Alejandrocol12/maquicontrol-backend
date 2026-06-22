package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Maquina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaquinaRepository extends JpaRepository<Maquina, Long> {
    List<Maquina> findByUsuarioId(Long usuarioId);
    Optional<Maquina> findByUsuarioIdAndNombre(Long usuarioId, String nombre);
}
