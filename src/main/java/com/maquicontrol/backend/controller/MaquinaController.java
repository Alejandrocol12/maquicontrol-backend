package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.service.MaquinaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maquinaria")
@CrossOrigin(origins = "*")
public class MaquinaController {

    @Autowired
    private MaquinaService maquinaService;

    @GetMapping
    public List<Maquina> obtenerTodas(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return maquinaService.obtenerTodas(userId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Maquina> obtenerPorId(@PathVariable Long id) {
        return maquinaService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Maquina registrar(@RequestBody Maquina maquina, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return maquinaService.guardar(userId, maquina);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Maquina> actualizar(@PathVariable Long id, @RequestBody Maquina maquina) {
        return ResponseEntity.ok(maquinaService.actualizar(id, maquina));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        maquinaService.eliminar(id, userId);
        return ResponseEntity.noContent().build();
    }
}
