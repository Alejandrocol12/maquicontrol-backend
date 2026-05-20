package com.maquicontrol.backend.repository;

import com.maquicontrol.backend.model.PagoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagoClienteRepository extends JpaRepository<PagoCliente, Long> {
    List<PagoCliente> findByCliente(String cliente);
    List<PagoCliente> findByEstado(String estado);
}