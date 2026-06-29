package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Mantenimiento;
import com.maquicontrol.backend.repository.MaquinaRepository;
import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.repository.UsuarioRepository;
import com.maquicontrol.backend.service.MantenimientoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/mantenimientos")
public class MantenimientoController {

    @Autowired private MantenimientoService mantenimientoService;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private OperadorRepository operadorRepo;
    @Autowired private MaquinaRepository maquinaRepo;

    // Operador: mantenimientos de su máquina asignada
    @GetMapping("/mi-maquina")
    public ResponseEntity<?> getMiMaquinaMantenimientos(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        var usuario = usuarioRepo.findById(userId).orElse(null);
        if (usuario == null || usuario.getOperadorId() == null)
            return ResponseEntity.ok(List.of());
        var operador = operadorRepo.findById(usuario.getOperadorId()).orElse(null);
        if (operador == null) return ResponseEntity.ok(List.of());
        var maquinas = maquinaRepo.findByUsuarioIdAndOperadorNombre(operador.getUsuarioId(), operador.getNombre());
        if (maquinas.isEmpty()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(mantenimientoService.obtenerPorMaquina(
            operador.getUsuarioId(), maquinas.get(0).getNombre()
        ));
    }

    @GetMapping
    public List<Mantenimiento> obtenerTodos(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return mantenimientoService.obtenerTodos(userId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Mantenimiento> obtenerPorId(@PathVariable Long id) {
        return mantenimientoService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/maquina/{nombre}")
    public List<Mantenimiento> obtenerPorMaquina(@PathVariable String nombre, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return mantenimientoService.obtenerPorMaquina(userId, nombre);
    }

    @PostMapping
    public Mantenimiento registrar(@RequestBody Mantenimiento mantenimiento, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return mantenimientoService.guardar(userId, mantenimiento);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Mantenimiento> actualizar(@PathVariable Long id, @RequestBody Mantenimiento mantenimiento, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Mantenimiento> existente = mantenimientoService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(mantenimientoService.actualizar(id, mantenimiento));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Mantenimiento> existente = mantenimientoService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        mantenimientoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
