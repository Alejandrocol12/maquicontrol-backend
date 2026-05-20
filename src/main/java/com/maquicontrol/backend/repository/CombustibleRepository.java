package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Combustible;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CombustibleRepository extends JpaRepository<Combustible, Long> {
    List<Combustible> findByMaquinaNombre(String maquinaNombre);
    void deleteByMaquinaNombre(String maquinaNombre);
}