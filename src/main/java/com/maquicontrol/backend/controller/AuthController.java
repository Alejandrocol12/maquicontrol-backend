package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.config.JwtUtil;
import com.maquicontrol.backend.model.Usuario;
import com.maquicontrol.backend.repository.UsuarioRepository;
import com.maquicontrol.backend.service.CodigoVerificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CodigoVerificacionService codigoService;

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

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
        return usuarioRepo.findById((Long) auth.getPrincipal())
                .map(u -> ResponseEntity.ok(userDto(u)))
                .orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody Map<String, String> body, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        return usuarioRepo.findById((Long) auth.getPrincipal()).map(u -> {
            if (body.containsKey("nombre") && body.get("nombre") != null && !body.get("nombre").isBlank())
                u.setNombre(body.get("nombre"));
            if (body.containsKey("empresa"))
                u.setEmpresa(body.get("empresa"));
            usuarioRepo.save(u);
            return ResponseEntity.ok(userDto(u));
        }).orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/enviar-codigo")
    public ResponseEntity<?> enviarCodigo(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        var opt = usuarioRepo.findById((Long) auth.getPrincipal());
        if (opt.isEmpty()) return ResponseEntity.status(401).build();
        Usuario u = opt.get();
        String codigo = codigoService.generar(u.getEmail());
        try {
            enviarEmailBrevo(u.getEmail(), u.getNombre(), codigo);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("error", "No se pudo enviar el correo: " + ex.getMessage()));
        }
    }

    @PostMapping("/pin/configurar")
    public ResponseEntity<?> configurarPin(@RequestBody Map<String, String> body, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        String pin = body.get("pin");
        if (pin == null || !pin.matches("\\d{4}"))
            return ResponseEntity.badRequest().body(Map.of("error", "El PIN debe ser exactamente 4 dígitos numéricos"));
        return usuarioRepo.findById((Long) auth.getPrincipal()).map(u -> {
            u.setPin(passwordEncoder.encode(pin));
            usuarioRepo.save(u);
            return ResponseEntity.ok(userDto(u));
        }).orElse(ResponseEntity.status(401).build());
    }

    @DeleteMapping("/pin")
    public ResponseEntity<?> eliminarPin(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        return usuarioRepo.findById((Long) auth.getPrincipal()).map(u -> {
            u.setPin(null);
            usuarioRepo.save(u);
            return ResponseEntity.ok(userDto(u));
        }).orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/pin/login")
    public ResponseEntity<?> loginPin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String pin = body.get("pin");
        if (email == null || pin == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Email y PIN requeridos"));
        return usuarioRepo.findByEmail(email)
            .filter(u -> u.isActivo() && u.getPin() != null && passwordEncoder.matches(pin, u.getPin()))
            .map(u -> ResponseEntity.ok(buildResponse(u)))
            .orElse(ResponseEntity.status(401).body(Map.of("error", "Email o PIN incorrecto")));
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        var opt = usuarioRepo.findById((Long) auth.getPrincipal());
        if (opt.isEmpty()) return ResponseEntity.status(401).build();
        Usuario u = opt.get();
        if (!codigoService.verificar(u.getEmail(), body.get("codigo")))
            return ResponseEntity.badRequest().body(Map.of("error", "Código incorrecto o expirado"));
        String nueva = body.get("nueva");
        if (nueva == null || nueva.length() < 6)
            return ResponseEntity.badRequest().body(Map.of("error", "La nueva contraseña debe tener al menos 6 caracteres"));
        u.setPassword(passwordEncoder.encode(nueva));
        usuarioRepo.save(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private void enviarEmailBrevo(String to, String nombre, String codigo) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        var restTemplate = new RestTemplate(factory);

        var headers = new HttpHeaders();
        headers.set("api-key", brevoApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String texto =
            "Hola " + nombre + ",\n\n" +
            "Tu código de verificación para cambiar la contraseña es:\n\n" +
            "        " + codigo + "\n\n" +
            "Este código expira en 15 minutos.\n" +
            "Si no solicitaste este cambio, ignora este mensaje.\n\n" +
            "— MaquiControl";

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of("name", "MaquiControl", "email", "alejorojas9.r@gmail.com"));
        payload.put("to", List.of(Map.of("email", to)));
        payload.put("subject", "MaquiControl — Código para cambiar contraseña");
        payload.put("textContent", texto);

        restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email",
            new HttpEntity<>(payload, headers), String.class);
    }

    private Map<String, Object> buildResponse(Usuario u) {
        return Map.of("token", jwtUtil.generate(u), "user", userDto(u));
    }

    private Map<String, Object> userDto(Usuario u) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", u.getId());
        dto.put("nombre", u.getNombre());
        dto.put("empresa", u.getEmpresa());
        dto.put("email", u.getEmail());
        dto.put("rol", u.getRol());
        dto.put("activo", u.isActivo());
        dto.put("operadorId", u.getOperadorId());
        dto.put("hasPin", u.getPin() != null);
        return dto;
    }
}
