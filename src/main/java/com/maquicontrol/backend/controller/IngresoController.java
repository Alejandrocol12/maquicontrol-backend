package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Ingreso;
import com.maquicontrol.backend.service.IngresoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ingresos")
@CrossOrigin(origins = "*")
public class IngresoController {

    @Autowired
    private IngresoService ingresoService;

    @GetMapping
    public List<Ingreso> obtenerTodos() {
        return ingresoService.obtenerTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ingreso> obtenerPorId(@PathVariable Long id) {
        return ingresoService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/maquina/{nombre}")
    public List<Ingreso> obtenerPorMaquina(@PathVariable String nombre) {
        return ingresoService.obtenerPorMaquina(nombre);
    }

    @PostMapping
    public Ingreso registrar(@RequestBody Ingreso ingreso) {
        return ingresoService.guardar(ingreso);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ingreso> actualizar(@PathVariable Long id, @RequestBody Ingreso ingreso) {
        return ResponseEntity.ok(ingresoService.actualizar(id, ingreso));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        ingresoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}