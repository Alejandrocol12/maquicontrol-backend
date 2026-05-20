package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Usuario;
import com.maquicontrol.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    @Autowired private UsuarioRepository usuarioRepo;

    @GetMapping
    public List<Usuario> listar() {
        return usuarioRepo.findAll();
    }

    @PutMapping("/{id}/rol")
    public ResponseEntity<?> actualizarRol(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return usuarioRepo.findById(id).map(u -> {
            u.setRol(body.get("rol"));
            return ResponseEntity.ok(usuarioRepo.save(u));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/activo")
    public ResponseEntity<?> actualizarActivo(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return usuarioRepo.findById(id).map(u -> {
            u.setActivo(body.get("activo"));
            return ResponseEntity.ok(usuarioRepo.save(u));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/vincular-operador")
    public ResponseEntity<?> vincularOperador(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return usuarioRepo.findById(id).map(u -> {
            u.setOperadorId(body.get("operadorId"));
            return ResponseEntity.ok(usuarioRepo.save(u));
        }).orElse(ResponseEntity.notFound().build());
    }
}
