package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Faena;
import com.maquicontrol.backend.service.FaenaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/faenas")
public class FaenaController {

    @Autowired
    private FaenaService faenaService;

    @GetMapping
    public List<Faena> obtenerTodas(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return faenaService.obtenerTodas(userId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Faena> obtenerPorId(@PathVariable Long id) {
        return faenaService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/maquina/{nombre}/activa")
    public ResponseEntity<Faena> obtenerActiva(@PathVariable String nombre, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return faenaService.obtenerActiva(userId, nombre)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Faena faena, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        try {
            return ResponseEntity.ok(faenaService.crear(userId, faena));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Faena> actualizar(@PathVariable Long id, @RequestBody Faena faena, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Faena> existente = faenaService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(faenaService.actualizar(id, faena));
    }

    // Ciclo del cliente: { corteDesde, corteHasta, metaHorasCorte }, todos opcionales
    @PutMapping("/{id}/ciclo")
    public ResponseEntity<?> actualizarCiclo(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Faena> existente = faenaService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        try {
            java.time.LocalDate desde = fechaOpc(body.get("corteDesde"));
            java.time.LocalDate hasta = fechaOpc(body.get("corteHasta"));
            Object m = body.get("metaHorasCorte");
            Double meta = m == null || String.valueOf(m).isBlank() ? null : Double.valueOf(String.valueOf(m).replace(',', '.'));
            return ResponseEntity.ok(faenaService.actualizarCiclo(id, desde, hasta, meta));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e instanceof java.time.DateTimeException ? "Fecha no válida" : e.getMessage()));
        }
    }

    private static java.time.LocalDate fechaOpc(Object v) {
        return v == null || String.valueOf(v).isBlank() ? null : java.time.LocalDate.parse(String.valueOf(v).substring(0, 10));
    }

    @PostMapping("/{id}/cerrar")
    public ResponseEntity<Faena> cerrar(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Faena> existente = faenaService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(faenaService.cerrar(id));
    }

    @PostMapping("/{id}/reabrir")
    public ResponseEntity<?> reabrir(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Faena> existente = faenaService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        try {
            return ResponseEntity.ok(faenaService.reabrir(id));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/deshacer-corte-operador")
    public ResponseEntity<Void> deshacerCorteOperador(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Faena> existente = faenaService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        faenaService.deshacerCorteOperadorPorFaena(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Faena> existente = faenaService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(faenaService.eliminar(userId, id));
    }

    // Registros que quedaron de periodos que ya no existen (se ven como "Sin periodo asignado")
    @GetMapping("/huerfanos")
    public ResponseEntity<?> contarHuerfanos(Authentication auth) {
        return ResponseEntity.ok(faenaService.contarHuerfanos((Long) auth.getPrincipal()));
    }

    @DeleteMapping("/huerfanos")
    public ResponseEntity<?> limpiarHuerfanos(Authentication auth) {
        return ResponseEntity.ok(faenaService.limpiarHuerfanos((Long) auth.getPrincipal()));
    }
}
