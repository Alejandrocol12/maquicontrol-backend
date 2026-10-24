package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Ingreso;
import com.maquicontrol.backend.repository.IngresoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.maquicontrol.backend.service.IngresoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ingresos")
public class IngresoController {

    @Autowired
    private IngresoService ingresoService;

    @Autowired
    private IngresoRepository ingresoRepository;

    // Sin parámetros devuelve todo. Con size (y page desde 0) devuelve una página, la más reciente primero,
    // y el total en la cabecera X-Total-Count.
    @GetMapping
    public ResponseEntity<List<Ingreso>> obtenerTodos(Authentication auth,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        Long userId = (Long) auth.getPrincipal();
        if (size == null) return ResponseEntity.ok(ingresoService.obtenerTodos(userId));
        var pagina = ingresoRepository.findByUsuarioId(userId, PageRequest.of(Math.max(page == null ? 0 : page, 0),
                Math.min(Math.max(size, 1), 500), Sort.by(Sort.Direction.DESC, "fecha").and(Sort.by(Sort.Direction.DESC, "id"))));
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(pagina.getTotalElements())).body(pagina.getContent());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ingreso> obtenerPorId(@PathVariable Long id) {
        return ingresoService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/maquina/{nombre}")
    public List<Ingreso> obtenerPorMaquina(@PathVariable String nombre, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ingresoService.obtenerPorMaquina(userId, nombre);
    }

    @PostMapping
    public Ingreso registrar(@RequestBody Ingreso ingreso, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ingresoService.guardar(userId, ingreso);
    }

    // Rellena horometroInicio/horometroFin de ingresos viejos, solo cuando el dato exacto
    // ya existe guardado en un HoraTrabajada vinculado. No estima ni adivina nada.
    @PostMapping("/backfill-horometro")
    public ResponseEntity<?> backfillHorometro(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        int actualizados = ingresoService.backfillHorometroExacto(userId);
        return ResponseEntity.ok(java.util.Map.of("actualizados", actualizados));
    }

    // Igual que arriba, pero para los que no se pudieron resolver exacto: los estima encadenando
    // el horómetro hacia atrás desde el valor actual de cada máquina.
    @PostMapping("/backfill-horometro-estimado")
    public ResponseEntity<?> backfillHorometroEstimado(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        int actualizados = ingresoService.backfillHorometroEstimado(userId);
        return ResponseEntity.ok(java.util.Map.of("actualizados", actualizados));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ingreso> actualizar(@PathVariable Long id, @RequestBody Ingreso ingreso, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Ingreso> existente = ingresoService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(ingresoService.actualizar(id, ingreso));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Ingreso> existente = ingresoService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        ingresoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
