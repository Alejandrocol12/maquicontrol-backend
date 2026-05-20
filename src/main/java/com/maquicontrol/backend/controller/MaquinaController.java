package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.service.MaquinaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maquinaria")
@CrossOrigin(origins = "*")
public class MaquinaController {

    @Autowired
    private MaquinaService maquinaService;

    // GET /api/maquinaria - Obtener todas las máquinas
    @GetMapping
    public List<Maquina> obtenerTodas() {
        return maquinaService.obtenerTodas();
    }

    // GET /api/maquinaria/{id} - Obtener una máquina
    @GetMapping("/{id}")
    public ResponseEntity<Maquina> obtenerPorId(@PathVariable Long id) {
        return maquinaService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/maquinaria - Registrar nueva máquina
    @PostMapping
    public Maquina registrar(@RequestBody Maquina maquina) {
        return maquinaService.guardar(maquina);
    }

    // PUT /api/maquinaria/{id} - Actualizar máquina
    @PutMapping("/{id}")
    public ResponseEntity<Maquina> actualizar(@PathVariable Long id, @RequestBody Maquina maquina) {
        return ResponseEntity.ok(maquinaService.actualizar(id, maquina));
    }

    // DELETE /api/maquinaria/{id} - Eliminar máquina
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        maquinaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}