package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.PagoCliente;
import com.maquicontrol.backend.repository.PagoClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PagoClienteService {

    @Autowired
    private PagoClienteRepository pagoRepository;

    public List<PagoCliente> obtenerTodos(Long userId) {
        return pagoRepository.findByUsuarioId(userId);
    }

    public Optional<PagoCliente> obtenerPorId(Long id) {
        return pagoRepository.findById(id);
    }

    public List<PagoCliente> obtenerPorCliente(Long userId, String cliente) {
        return pagoRepository.findByUsuarioIdAndCliente(userId, cliente);
    }

    public List<PagoCliente> obtenerPorEstado(Long userId, String estado) {
        return pagoRepository.findByUsuarioIdAndEstado(userId, estado);
    }

    public PagoCliente guardar(Long userId, PagoCliente pago) {
        pago.setUsuarioId(userId);
        pago.setSaldoPendiente(pago.getValorTotal() - pago.getValorPagado());
        if (pago.getSaldoPendiente() <= 0) {
            pago.setEstado("Pagado");
        } else if (pago.getValorPagado() > 0) {
            pago.setEstado("Parcial");
        } else {
            pago.setEstado("Deuda");
        }
        return pagoRepository.save(pago);
    }

    public PagoCliente actualizar(Long id, PagoCliente pagoActualizado) {
        PagoCliente pago = pagoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Pago no encontrado"));
        pago.setCliente(pagoActualizado.getCliente());
        pago.setMaquinaNombre(pagoActualizado.getMaquinaNombre());
        pago.setDescripcion(pagoActualizado.getDescripcion());
        pago.setValorTotal(pagoActualizado.getValorTotal());
        pago.setValorPagado(pagoActualizado.getValorPagado());
        pago.setSaldoPendiente(pagoActualizado.getValorTotal() - pagoActualizado.getValorPagado());
        pago.setFecha(pagoActualizado.getFecha());
        if (pago.getSaldoPendiente() <= 0) {
            pago.setEstado("Pagado");
        } else if (pago.getValorPagado() > 0) {
            pago.setEstado("Parcial");
        } else {
            pago.setEstado("Deuda");
        }
        return pagoRepository.save(pago);
    }

    public void eliminar(Long id) {
        pagoRepository.deleteById(id);
    }
}