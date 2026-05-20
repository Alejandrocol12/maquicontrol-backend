package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.Salario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalarioRepository extends JpaRepository<Salario, Long> {
    List<Salario> findByOperadorNombre(String operadorNombre);
    void deleteByOperadorNombre(String operadorNombre);
}