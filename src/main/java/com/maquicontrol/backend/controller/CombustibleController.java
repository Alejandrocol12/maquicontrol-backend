package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Combustible;
import com.maquicontrol.backend.service.CombustibleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/combustible")
@CrossOrigin(origins = "*")
public class CombustibleController {

    @Autowired
    private CombustibleService combustibleService;

    @GetMapping
    public List<Combustible> obtenerTodos() {
        return combustibleService.obtenerTodos();
    }

    @GetMapping("/maquina/{nombre}")
    public List<Combustible> obtenerPorMaquina(@PathVariable String nombre) {
        return combustibleService.obtenerPorMaquina(nombre);
    }

    @PostMapping
    public Combustible registrar(@RequestBody Combustible combustible) {
        return combustibleService.guardar(combustible);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        combustibleService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}