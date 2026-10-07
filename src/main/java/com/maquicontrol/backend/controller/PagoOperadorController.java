package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.PagoOperador;
import com.maquicontrol.backend.service.PagoOperadorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/pagos-operador")
public class PagoOperadorController {

    @Autowired
    private PagoOperadorService pagoService;

    @GetMapping
    public List<PagoOperador> obtenerTodos(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return pagoService.obtenerTodos(userId);
    }

    // Pagos anotados antes de que Pago Operador se registrara en Gastos
    @GetMapping("/sin-gasto")
    public List<PagoOperador> sinGasto(Authentication auth) {
        return pagoService.sinGasto((Long) auth.getPrincipal());
    }

    @PostMapping("/pasar-a-gastos")
    public java.util.Map<String, Integer> pasarAGastos(Authentication auth) {
        return java.util.Map.of("pasados", pagoService.pasarAGastos((Long) auth.getPrincipal()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PagoOperador> obtenerPorId(@PathVariable Long id) {
        return pagoService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/operador/{nombre}")
    public List<PagoOperador> obtenerPorOperador(@PathVariable String nombre, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return pagoService.obtenerPorOperador(userId, nombre);
    }

    @PostMapping
    public PagoOperador registrar(@RequestBody PagoOperador pago, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return pagoService.guardar(userId, pago);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PagoOperador> actualizar(@PathVariable Long id, @RequestBody PagoOperador pago, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<PagoOperador> existente = pagoService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(pagoService.actualizar(id, pago));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<PagoOperador> existente = pagoService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        pagoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
