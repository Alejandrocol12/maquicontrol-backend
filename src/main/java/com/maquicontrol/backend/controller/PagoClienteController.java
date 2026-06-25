package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.PagoCliente;
import com.maquicontrol.backend.service.PagoClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/pagos")
public class PagoClienteController {

    @Autowired
    private PagoClienteService pagoService;

    @GetMapping
    public List<PagoCliente> obtenerTodos(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return pagoService.obtenerTodos(userId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PagoCliente> obtenerPorId(@PathVariable Long id) {
        return pagoService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cliente/{nombre}")
    public List<PagoCliente> obtenerPorCliente(@PathVariable String nombre, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return pagoService.obtenerPorCliente(userId, nombre);
    }

    @GetMapping("/estado/{estado}")
    public List<PagoCliente> obtenerPorEstado(@PathVariable String estado, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return pagoService.obtenerPorEstado(userId, estado);
    }

    @PostMapping
    public PagoCliente registrar(@RequestBody PagoCliente pago, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return pagoService.guardar(userId, pago);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PagoCliente> actualizar(@PathVariable Long id, @RequestBody PagoCliente pago, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<PagoCliente> existente = pagoService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(pagoService.actualizar(id, pago));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<PagoCliente> existente = pagoService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        pagoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
