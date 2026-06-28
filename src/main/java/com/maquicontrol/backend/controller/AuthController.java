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
public class AuthController {

    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CodigoVerificacionService codigoService;

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    @Value("${brevo.sender-email:alejorojas9.r@gmail.com}")
    private String brevoSenderEmail;

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

    @PostMapping("/solicitar-cambio-email")
    public ResponseEntity<?> solicitarCambioEmail(@RequestBody Map<String, String> body, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        String raw = body.get("nuevoEmail");
        if (raw == null || !raw.contains("@"))
            return ResponseEntity.badRequest().body(Map.of("error", "Correo no válido"));
        String nuevoEmail = raw.trim().toLowerCase();
        if (usuarioRepo.existsByEmail(nuevoEmail))
            return ResponseEntity.badRequest().body(Map.of("error", "Ese correo ya está registrado en otra cuenta"));
        var opt = usuarioRepo.findById((Long) auth.getPrincipal());
        if (opt.isEmpty()) return ResponseEntity.status(401).build();
        Usuario u = opt.get();
        if (u.getEmail().equalsIgnoreCase(nuevoEmail))
            return ResponseEntity.badRequest().body(Map.of("error", "Ese ya es tu correo actual"));
        String codigo = codigoService.generar(nuevoEmail);
        try {
            enviarEmailCambioCorreo(nuevoEmail, u.getNombre(), codigo);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("error", "No se pudo enviar el correo: " + ex.getMessage()));
        }
    }

    @PostMapping("/confirmar-cambio-email")
    public ResponseEntity<?> confirmarCambioEmail(@RequestBody Map<String, String> body, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        String raw = body.get("nuevoEmail");
        String codigo = body.get("codigo");
        if (raw == null || codigo == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Datos incompletos"));
        String nuevoEmail = raw.trim().toLowerCase();
        if (!codigoService.verificar(nuevoEmail, codigo))
            return ResponseEntity.badRequest().body(Map.of("error", "Código incorrecto o expirado"));
        if (usuarioRepo.existsByEmail(nuevoEmail))
            return ResponseEntity.badRequest().body(Map.of("error", "Ese correo ya está registrado en otra cuenta"));
        return usuarioRepo.findById((Long) auth.getPrincipal()).map(u -> {
            u.setEmail(nuevoEmail);
            usuarioRepo.save(u);
            return ResponseEntity.ok(userDto(u));
        }).orElse(ResponseEntity.status(401).build());
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

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of("name", "MaquiControl", "email", brevoSenderEmail));
        payload.put("to", List.of(Map.of("email", to)));
        payload.put("subject", "MaquiControl — Código para cambiar contraseña");
        payload.put("htmlContent", buildEmailRecuperacion(nombre, codigo));

        restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email",
            new HttpEntity<>(payload, headers), String.class);
    }

    private void enviarEmailCambioCorreo(String to, String nombre, String codigo) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        var restTemplate = new RestTemplate(factory);

        var headers = new HttpHeaders();
        headers.set("api-key", brevoApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of("name", "MaquiControl", "email", brevoSenderEmail));
        payload.put("to", List.of(Map.of("email", to)));
        payload.put("subject", "MaquiControl — Confirma tu nuevo correo");
        payload.put("htmlContent", buildEmailCambioCorreo(nombre, codigo));

        restTemplate.postForEntity("https://api.brevo.com/v3/smtp/email",
            new HttpEntity<>(payload, headers), String.class);
    }

    private String buildEmailCambioCorreo(String nombre, String codigo) {
        return "<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'></head>" +
        "<body style='margin:0;padding:0;background:#f0f4f8;font-family:Arial,Helvetica,sans-serif;'>" +
        "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f0f4f8;padding:32px 16px;'><tr><td align='center'>" +
        "<table width='600' cellpadding='0' cellspacing='0' style='max-width:600px;width:100%;'>" +
        "<tr><td style='background:linear-gradient(135deg,#0d1b2a 0%,#10263c 100%);border-radius:16px 16px 0 0;padding:32px 40px;text-align:center;'>" +
        "<div style='font-size:32px;font-weight:900;letter-spacing:-1px;'><span style='color:#f5a623;'>Maqui</span><span style='color:#fff;'>Control</span></div>" +
        "</td></tr>" +
        "<tr><td style='background:#fff;padding:40px 40px 36px;text-align:center;'>" +
        "<div style='width:64px;height:64px;background:#e8f5e9;border:2px solid #a5d6a7;border-radius:50%;margin:0 auto 20px;font-size:28px;line-height:64px;'>&#9993;</div>" +
        "<h1 style='margin:0 0 10px;font-size:22px;color:#0d1b2a;font-weight:800;'>Confirma tu nuevo correo</h1>" +
        "<p style='margin:0 0 28px;color:#6b7a8d;font-size:14px;line-height:1.6;max-width:400px;margin-left:auto;margin-right:auto;'>" +
        "Hola <strong>" + nombre + "</strong>, usa este código para confirmar el cambio de correo en tu cuenta. Expira en <strong>15 minutos</strong>.</p>" +
        "<table cellpadding='0' cellspacing='0' style='margin:0 auto 28px;'><tr>" +
        "<td style='background:linear-gradient(135deg,#0d1b2a,#10263c);border-radius:14px;padding:24px 48px;text-align:center;'>" +
        "<div style='font-size:11px;color:rgba(255,255,255,0.5);text-transform:uppercase;letter-spacing:2px;margin-bottom:10px;'>Código de verificación</div>" +
        "<div style='font-size:42px;font-weight:900;color:#f5a623;letter-spacing:10px;font-family:Georgia,monospace;'>" + codigo + "</div>" +
        "<div style='font-size:11px;color:rgba(255,255,255,0.35);margin-top:10px;'>Válido por 15 minutos</div>" +
        "</td></tr></table>" +
        "<div style='background:#fdf3f3;border:1px solid #f5c6c6;border-radius:10px;padding:14px 18px;text-align:left;'>" +
        "<div style='font-size:12px;color:#c0392b;line-height:1.6;'>&#128274; <strong>Si no solicitaste este cambio</strong>, ignora este correo. Tu correo actual no será modificado.</div></div>" +
        "</td></tr>" +
        "<tr><td style='background:#f8fafc;border-radius:0 0 16px 16px;padding:20px 40px;text-align:center;border-top:1px solid #e2e8f0;'>" +
        "<div style='font-size:14px;font-weight:800;color:#0d1b2a;'><span style='color:#f5a623;'>Maqui</span>Control</div>" +
        "</td></tr>" +
        "</table></td></tr></table></body></html>";
    }

    private String buildEmailRecuperacion(String nombre, String codigo) {
        return "<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
        "<body style='margin:0;padding:0;background:#f0f4f8;font-family:Arial,Helvetica,sans-serif;'>" +
        "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f0f4f8;padding:32px 16px;'><tr><td align='center'>" +
        "<table width='600' cellpadding='0' cellspacing='0' style='max-width:600px;width:100%;'>" +

        // HEADER
        "<tr><td style='background:linear-gradient(135deg,#0d1b2a 0%,#10263c 100%);border-radius:16px 16px 0 0;padding:32px 40px;text-align:center;'>" +
        "<div style='font-size:32px;font-weight:900;letter-spacing:-1px;line-height:1;'>" +
        "<span style='color:#f5a623;'>Maqui</span><span style='color:#fff;'>Control</span></div>" +
        "</td></tr>" +

        // BODY
        "<tr><td style='background:#fff;padding:40px 40px 36px;text-align:center;'>" +
        "<div style='width:64px;height:64px;background:#fff8e7;border:2px solid #f5e0a0;border-radius:50%;margin:0 auto 20px;font-size:28px;line-height:64px;text-align:center;'>&#128272;</div>" +
        "<h1 style='margin:0 0 10px;font-size:22px;color:#0d1b2a;font-weight:800;'>Recuperar contraseña</h1>" +
        "<p style='margin:0 0 28px;color:#6b7a8d;font-size:14px;line-height:1.6;max-width:400px;margin-left:auto;margin-right:auto;'>" +
        "Hola <strong>" + nombre + "</strong>, usa este código para cambiar tu contraseña. Expira en <strong>15 minutos</strong>.</p>" +

        // CÓDIGO
        "<table cellpadding='0' cellspacing='0' style='margin:0 auto 28px;'><tr>" +
        "<td style='background:linear-gradient(135deg,#0d1b2a,#10263c);border-radius:14px;padding:24px 48px;text-align:center;'>" +
        "<div style='font-size:11px;color:rgba(255,255,255,0.5);text-transform:uppercase;letter-spacing:2px;margin-bottom:10px;'>Código de verificación</div>" +
        "<div style='font-size:42px;font-weight:900;color:#f5a623;letter-spacing:10px;font-family:Georgia,monospace;'>" + codigo + "</div>" +
        "<div style='font-size:11px;color:rgba(255,255,255,0.35);margin-top:10px;'>Válido por 15 minutos</div>" +
        "</td></tr></table>" +

        // INSTRUCCIONES
        "<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:16px 20px;text-align:left;margin-bottom:20px;'>" +
        "<div style='font-size:12px;font-weight:700;color:#0d1b2a;margin-bottom:8px;'>&#10067; ¿Cómo usarlo?</div>" +
        "<div style='font-size:12px;color:#6b7a8d;line-height:1.8;'>" +
        "1. Inicia sesión con tu correo y contraseña actual.<br>" +
        "2. Ve a <strong>Perfil &rarr; Cambiar contraseña</strong>.<br>" +
        "3. Ingresa este código y escribe tu nueva contraseña.</div></div>" +

        // ALERTA
        "<div style='background:#fdf3f3;border:1px solid #f5c6c6;border-radius:10px;padding:14px 18px;text-align:left;'>" +
        "<div style='font-size:12px;color:#c0392b;line-height:1.6;'>&#128274; <strong>Si no solicitaste este cambio</strong>, ignora este correo. " +
        "Tu contraseña actual seguirá siendo la misma.</div></div>" +
        "</td></tr>" +

        // FOOTER
        "<tr><td style='background:#f8fafc;border-radius:0 0 16px 16px;padding:20px 40px;text-align:center;border-top:1px solid #e2e8f0;'>" +
        "<div style='font-size:14px;font-weight:800;color:#0d1b2a;margin-bottom:4px;'><span style='color:#f5a623;'>Maqui</span>Control</div>" +
        "<p style='margin:4px 0 0;color:#9aa5b4;font-size:11px;'>Recibiste este correo porque solicitaste cambiar tu contraseña.</p>" +
        "</td></tr>" +

        "</table></td></tr></table></body></html>";
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
