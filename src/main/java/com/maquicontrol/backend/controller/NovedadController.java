package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Novedad;
import com.maquicontrol.backend.repository.NovedadRepository;
import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/novedades")
public class NovedadController {

    @Autowired private NovedadRepository novedadRepo;
    @Autowired private OperadorRepository operadorRepo;
    @Autowired private UsuarioRepository usuarioRepo;

    // Admin: ver todas las novedades de su cuenta
    @GetMapping
    public List<Novedad> getAll(Authentication auth) {
        return novedadRepo.findByUsuarioIdOrderByFechaDesc((Long) auth.getPrincipal());
    }

    // Operador: ver sus propias novedades
    @GetMapping("/mis")
    public ResponseEntity<?> getMis(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        var usuario = usuarioRepo.findById(userId).orElse(null);
        if (usuario == null || usuario.getOperadorId() == null)
            return ResponseEntity.status(403).build();
        return ResponseEntity.ok(novedadRepo.findByOperadorIdOrderByFechaDesc(usuario.getOperadorId()));
    }

    // Operador: crear novedad
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, String> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        var usuario = usuarioRepo.findById(userId).orElse(null);
        if (usuario == null || usuario.getOperadorId() == null)
            return ResponseEntity.status(403).build();
        var operador = operadorRepo.findById(usuario.getOperadorId()).orElse(null);
        if (operador == null)
            return ResponseEntity.status(404).build();

        Novedad n = new Novedad();
        n.setUsuarioId(operador.getUsuarioId());
        n.setOperadorId(operador.getId());
        n.setOperadorNombre(operador.getNombre());
        n.setMaquinaNombre(body.get("maquinaNombre"));
        n.setTipo(body.get("tipo"));
        n.setDescripcion(body.get("descripcion"));
        n.setFecha(LocalDate.now());
        n.setEstado("pendiente");

        return ResponseEntity.ok(novedadRepo.save(n));
    }

    // Admin: marcar novedad como revisada
    @PutMapping("/{id}/estado")
    public ResponseEntity<?> actualizarEstado(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return novedadRepo.findById(id).map(n -> {
            if (!userId.equals(n.getUsuarioId())) return ResponseEntity.status(403).<Novedad>build();
            n.setEstado(body.getOrDefault("estado", "revisada"));
            return ResponseEntity.ok(novedadRepo.save(n));
        }).orElse(ResponseEntity.notFound().build());
    }
}
