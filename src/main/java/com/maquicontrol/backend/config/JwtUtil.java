package com.maquicontrol.backend.config;

import com.maquicontrol.backend.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    // La clave que antes venía escrita en el código: es pública, así que ya no se acepta
    private static final String CLAVE_ANTIGUA = "maquicontrol-super-secret-key-2024-segura-256bits!!";

    @Value("${jwt.secret:}")
    private String secret;

    @PostConstruct
    void validarClave() {
        if (secret == null || secret.length() < 32 || CLAVE_ANTIGUA.equals(secret)) {
            log.warn("JWT_SECRET no está definida (o es demasiado corta). Se usa una clave temporal: las sesiones se cierran en cada reinicio. Define JWT_SECRET en Railway.");
            byte[] b = new byte[48];
            new SecureRandom().nextBytes(b);
            secret = Base64.getEncoder().encodeToString(b);
        }
    }

    private static final long EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000; // 7 días

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Usuario user, String sesionId) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("id", user.getId())
                .claim("nombre", user.getNombre())
                .claim("empresa", user.getEmpresa())
                .claim("rol", user.getRol())
                .claim("sid", sesionId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
