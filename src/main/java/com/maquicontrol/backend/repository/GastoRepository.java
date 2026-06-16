package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Gasto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GastoRepository extends JpaRepository<Gasto, Long> {
    List<Gasto> findByUsuarioId(Long usuarioId);
    List<Gasto> findByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);
    List<Gasto> findByMaquinaNombre(String maquinaNombre);
    void deleteByMaquinaNombre(String maquinaNombre);
    void deleteByUsuarioIdAndMaquinaNombre(Long usuarioId, String maquinaNombre);
}
