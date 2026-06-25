package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Combustible;
import com.maquicontrol.backend.service.CombustibleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/combustible")
public class CombustibleController {

    @Autowired
    private CombustibleService combustibleService;

    @GetMapping
    public List<Combustible> obtenerTodos(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return combustibleService.obtenerTodos(userId);
    }

    @GetMapping("/maquina/{nombre}")
    public List<Combustible> obtenerPorMaquina(@PathVariable String nombre, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return combustibleService.obtenerPorMaquina(userId, nombre);
    }

    @PostMapping
    public Combustible registrar(@RequestBody Combustible combustible, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return combustibleService.guardar(userId, combustible);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        combustibleService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
