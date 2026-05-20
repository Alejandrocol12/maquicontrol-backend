package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.PagoCliente;
import com.maquicontrol.backend.service.PagoClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pagos")
@CrossOrigin(origins = "*")
public class PagoClienteController {

    @Autowired
    private PagoClienteService pagoService;

    @GetMapping
    public List<PagoCliente> obtenerTodos() {
        return pagoService.obtenerTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PagoCliente> obtenerPorId(@PathVariable Long id) {
        return pagoService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cliente/{nombre}")
    public List<PagoCliente> obtenerPorCliente(@PathVariable String nombre) {
        return pagoService.obtenerPorCliente(nombre);
    }

    @GetMapping("/estado/{estado}")
    public List<PagoCliente> obtenerPorEstado(@PathVariable String estado) {
        return pagoService.obtenerPorEstado(estado);
    }

    @PostMapping
    public PagoCliente registrar(@RequestBody PagoCliente pago) {
        return pagoService.guardar(pago);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PagoCliente> actualizar(@PathVariable Long id, @RequestBody PagoCliente pago) {
        return ResponseEntity.ok(pagoService.actualizar(id, pago));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        pagoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}