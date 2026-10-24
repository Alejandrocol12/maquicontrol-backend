package com.maquicontrol.backend.config;

import com.maquicontrol.backend.model.SesionUsuario;
import com.maquicontrol.backend.repository.SesionUsuarioRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SesionUsuarioRepository sesionRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isValid(token)) {
                Claims claims = jwtUtil.parse(token);
                Long userId = claims.get("id", Long.class);
                String rol = claims.get("rol", String.class);
                String sid = claims.get("sid", String.class);
                // Un token con sesión solo sirve mientras la sesión exista y no esté cerrada.
                // Los tokens anteriores a las sesiones no llevan sid y siguen valiendo hasta que expiren.
                if (sid != null && !sesionActiva(sid, userId)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"Tu sesión se cerró. Inicia sesión de nuevo.\"}");
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
                auth.setDetails(sid);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private boolean sesionActiva(String sid, Long userId) {
        Optional<SesionUsuario> opt = sesionRepo.findById(sid);
        if (opt.isEmpty()) return false;
        SesionUsuario s = opt.get();
        if (s.isRevocada() || !s.getUsuarioId().equals(userId)) return false;
        // La última actividad se anota como mucho cada 5 minutos para no escribir en cada petición
        LocalDateTime ahora = LocalDateTime.now();
        if (s.getUltimaActividad() == null || s.getUltimaActividad().isBefore(ahora.minusMinutes(5))) {
            s.setUltimaActividad(ahora);
            sesionRepo.save(s);
        }
        return true;
    }
}
