package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.ParteDiario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParteDiarioRepository extends JpaRepository<ParteDiario, Long> {
    List<ParteDiario> findByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);
    Optional<ParteDiario> findByUsuarioIdAndMaquinaNombreAndFecha(Long usuarioId, String maquinaNombre, LocalDate fecha);
}
