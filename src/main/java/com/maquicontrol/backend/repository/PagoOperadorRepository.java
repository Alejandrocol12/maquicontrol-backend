package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.PagoOperador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagoOperadorRepository extends JpaRepository<PagoOperador, Long> {
    List<PagoOperador> findByUsuarioId(Long usuarioId);
    List<PagoOperador> findByUsuarioIdAndOperadorNombre(Long usuarioId, String operadorNombre);
}
