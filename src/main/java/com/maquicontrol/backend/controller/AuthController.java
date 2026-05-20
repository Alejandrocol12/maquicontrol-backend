package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.config.JwtUtil;
import com.maquicontrol.backend.model.Usuario;
import com.maquicontrol.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (usuarioRepo.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El correo ya está registrado"));
        }
        Usuario u = new Usuario();
        u.setNombre(body.get("nombre"));
        u.setEmpresa(body.get("empresa"));
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(body.get("password")));
        u.setRol("ADMIN");
        usuarioRepo.save(u);
        return ResponseEntity.ok(buildResponse(u));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        return usuarioRepo.findByEmail(body.get("email"))
                .filter(u -> u.isActivo() && passwordEncoder.matches(body.get("password"), u.getPassword()))
                .map(u -> ResponseEntity.ok(buildResponse(u)))
                .orElse(ResponseEntity.status(401).body(Map.of("error", "Correo o contraseña incorrectos")));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        return usuarioRepo.findByEmail(auth.getName())
                .map(u -> ResponseEntity.ok(userDto(u)))
                .orElse(ResponseEntity.status(401).build());
    }

    private Map<String, Object> buildResponse(Usuario u) {
        return Map.of("token", jwtUtil.generate(u), "user", userDto(u));
    }

    private Map<String, Object> userDto(Usuario u) {
        return Map.of(
                "id", u.getId(),
                "nombre", u.getNombre(),
                "empresa", u.getEmpresa(),
                "email", u.getEmail(),
                "rol", u.getRol()
        );
    }
}
