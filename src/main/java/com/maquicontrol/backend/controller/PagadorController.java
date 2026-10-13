package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Pagador;
import com.maquicontrol.backend.repository.GastoRepository;
import com.maquicontrol.backend.repository.PagadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Lista de personas que pagan gastos. Renombrar a alguien lo renombra en todos sus gastos;
// solo se puede quitar a quien no tiene gastos.
@RestController
@RequestMapping("/api/pagadores")
public class PagadorController {

    @Autowired private PagadorRepository pagadorRepo;
    @Autowired private GastoRepository gastoRepo;

    @GetMapping
    public List<Pagador> listar(Authentication auth) {
        return pagadorRepo.findByUsuarioIdOrderByNombreAsc((Long) auth.getPrincipal());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, String> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String nombre = limpiar(body.get("nombre"));
        String error = validar(userId, nombre, null);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));
        Pagador p = new Pagador();
        p.setUsuarioId(userId);
        p.setNombre(nombre);
        return ResponseEntity.ok(pagadorRepo.save(p));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> renombrar(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Pagador p = pagadorRepo.findById(id).filter(x -> userId.equals(x.getUsuarioId())).orElse(null);
        if (p == null) return ResponseEntity.status(403).build();
        String nombre = limpiar(body.get("nombre"));
        String error = validar(userId, nombre, p);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("error", error));
        String viejo = p.getNombre();
        p.setNombre(nombre);
        pagadorRepo.save(p);
        gastoRepo.actualizarPagador(userId, viejo, nombre);
        return ResponseEntity.ok(p);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> quitar(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Pagador p = pagadorRepo.findById(id).filter(x -> userId.equals(x.getUsuarioId())).orElse(null);
        if (p == null) return ResponseEntity.status(403).build();
        long usos = gastoRepo.countByUsuarioIdAndPagadoPor(userId, p.getNombre());
        if (usos > 0) {
            return ResponseEntity.status(409).body(Map.of("error", "Tiene " + usos + " gasto(s) registrados. Cámbiales la persona antes de quitarla."));
        }
        pagadorRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private static String limpiar(String s) { return s == null ? "" : s.trim().replaceAll("\s+", " "); }

    private String validar(Long userId, String nombre, Pagador actual) {
        if (nombre.isEmpty()) return "Escribe un nombre.";
        if (nombre.length() > 60) return "El nombre es muy largo (máximo 60 letras).";
        if (nombre.equalsIgnoreCase("Sin asignar")) return "Ese nombre está reservado.";
        boolean igualAlActual = actual != null && actual.getNombre().equalsIgnoreCase(nombre);
        if (!igualAlActual && pagadorRepo.existsByUsuarioIdAndNombreIgnoreCase(userId, nombre)) return "Ya existe una persona con ese nombre.";
        return null;
    }
}
