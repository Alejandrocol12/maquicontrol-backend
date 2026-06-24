package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.service.MaquinaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @PutMapping("/{id}/ubicacion")
    public ResponseEntity<?> actualizarUbicacion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return maquinaService.obtenerPorId(id).map(m -> {
            m.setLatitud(body.get("latitud") != null ? ((Number) body.get("latitud")).doubleValue() : null);
            m.setLongitud(body.get("longitud") != null ? ((Number) body.get("longitud")).doubleValue() : null);
            m.setUbicacionNombre(body.get("ubicacionNombre") != null ? body.get("ubicacionNombre").toString() : null);
            return ResponseEntity.ok(maquinaService.guardarDirecto(m));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        maquinaService.eliminar(id, userId);
        return ResponseEntity.noContent().build();
    }
}
