package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.EnlaceCompartido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnlaceCompartidoRepository extends JpaRepository<EnlaceCompartido, Long> {
    List<EnlaceCompartido> findByUsuarioIdAndActivoTrue(Long usuarioId);
    Optional<EnlaceCompartido> findByTokenAndActivoTrue(String token);
    Optional<EnlaceCompartido> findByToken(String token);
}
