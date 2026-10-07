package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Corte;
import com.maquicontrol.backend.service.CorteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cortes")
public class CorteController {

    @Autowired
    private CorteService corteService;

    @GetMapping
    public List<Corte> obtenerTodos(Authentication auth) {
        return corteService.obtenerTodos((Long) auth.getPrincipal());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            Long faenaId = body.get("faenaId") == null ? null : Long.valueOf(String.valueOf(body.get("faenaId")));
            LocalDate inicio = fecha(body.get("fechaInicio"));
            LocalDate fin = fecha(body.get("fechaFin"));
            boolean crearCobro = !Boolean.FALSE.equals(body.get("crearCobro"));
            boolean conOperador = !Boolean.FALSE.equals(body.get("conOperador"));
            return ResponseEntity.ok(corteService.crear(userId, faenaId, inicio, fin, crearCobro, conOperador));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error",
                e instanceof java.time.DateTimeException ? "Fecha no válida" : e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, Authentication auth) {
        try {
            corteService.eliminar((Long) auth.getPrincipal(), id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    private LocalDate fecha(Object v) {
        return v == null || String.valueOf(v).isBlank() ? null : LocalDate.parse(String.valueOf(v));
    }
}
