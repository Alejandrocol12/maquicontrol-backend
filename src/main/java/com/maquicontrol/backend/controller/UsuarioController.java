package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Usuario;
import com.maquicontrol.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        if (usuarioRepo.existsByEmail(email))
            return ResponseEntity.badRequest().body(Map.of("error", "El correo ya está registrado"));
        Usuario u = new Usuario();
        u.setNombre((String) body.get("nombre"));
        u.setEmpresa(body.get("empresa") != null ? (String) body.get("empresa") : "");
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode((String) body.get("password")));
        u.setRol(body.get("rol") != null ? (String) body.get("rol") : "operador");
        if (body.get("operadorId") != null)
            u.setOperadorId(((Number) body.get("operadorId")).longValue());
        return ResponseEntity.ok(usuarioRepo.save(u));
    }

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
