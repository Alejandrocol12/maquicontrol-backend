package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Faena;
import com.maquicontrol.backend.service.FaenaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faenas")
@CrossOrigin(origins = "*")
public class FaenaController {

    @Autowired
    private FaenaService faenaService;

    @GetMapping
    public List<Faena> obtenerTodas(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return faenaService.obtenerTodas(userId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Faena> obtenerPorId(@PathVariable Long id) {
        return faenaService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/maquina/{nombre}/activa")
    public ResponseEntity<Faena> obtenerActiva(@PathVariable String nombre, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return faenaService.obtenerActiva(userId, nombre)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Faena crear(@RequestBody Faena faena, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return faenaService.crear(userId, faena);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Faena> actualizar(@PathVariable Long id, @RequestBody Faena faena) {
        return ResponseEntity.ok(faenaService.actualizar(id, faena));
    }

    @PostMapping("/{id}/cerrar")
    public ResponseEntity<Faena> cerrar(@PathVariable Long id) {
        return ResponseEntity.ok(faenaService.cerrar(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        faenaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
