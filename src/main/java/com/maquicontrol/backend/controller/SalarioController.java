package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Salario;
import com.maquicontrol.backend.service.SalarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/salarios")
@CrossOrigin(origins = "*")
public class SalarioController {

    @Autowired
    private SalarioService salarioService;

    @GetMapping
    public List<Salario> obtenerTodos(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return salarioService.obtenerTodos(userId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Salario> obtenerPorId(@PathVariable Long id) {
        return salarioService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/operador/{nombre}")
    public List<Salario> obtenerPorOperador(@PathVariable String nombre, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return salarioService.obtenerPorOperador(userId, nombre);
    }

    @PostMapping
    public Salario registrar(@RequestBody Salario salario, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return salarioService.guardar(userId, salario);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Salario> actualizar(@PathVariable Long id, @RequestBody Salario salario) {
        return ResponseEntity.ok(salarioService.actualizar(id, salario));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        salarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
