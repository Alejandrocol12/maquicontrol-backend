package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Pagador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagadorRepository extends JpaRepository<Pagador, Long> {
    List<Pagador> findByUsuarioIdOrderByNombreAsc(Long usuarioId);
    boolean existsByUsuarioIdAndNombreIgnoreCase(Long usuarioId, String nombre);
}
