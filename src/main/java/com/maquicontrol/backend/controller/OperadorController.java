package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Operador;
import com.maquicontrol.backend.model.Periodo;
import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.service.OperadorService;
import com.maquicontrol.backend.service.PeriodoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/operadores")
public class OperadorController {

    @Autowired private OperadorService operadorService;
    @Autowired private PeriodoService periodoService;
    @Autowired private OperadorRepository operadorRepository;

    @GetMapping
    public List<Operador> listar(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return operadorService.obtenerTodos(userId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Operador> obtener(@PathVariable Long id) {
        return operadorService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Operador crear(@RequestBody Operador operador, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return operadorService.guardar(userId, operador);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Operador> actualizar(@PathVariable Long id, @RequestBody Operador operador, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Operador> existente = operadorService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(operadorService.actualizar(id, operador));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Operador> existente = operadorService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        operadorService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // --- Telegram ---

    @GetMapping("/{id}/telegram-code")
    public ResponseEntity<Map<String, Object>> getTelegramCode(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return operadorService.obtenerPorId(id)
            .filter(op -> userId.equals(op.getUsuarioId()))
            .map(op -> {
                String code = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
                op.setTelegramLinkCode(code);
                operadorRepository.save(op);
                return ResponseEntity.ok(Map.<String, Object>of(
                    "code", code,
                    "vinculado", op.getTelegramChatId() != null,
                    "deepLink", "https://t.me/MaquiControlBot?start=" + code
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/telegram-link")
    public ResponseEntity<Void> unlinkTelegram(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return operadorService.obtenerPorId(id)
            .filter(op -> userId.equals(op.getUsuarioId()))
            .map(op -> {
                op.setTelegramChatId(null);
                op.setTelegramLinkCode(null);
                operadorRepository.save(op);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // --- Periodos ---

    @GetMapping("/{id}/periodos")
    public List<Periodo> listarPeriodos(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return periodoService.obtenerPorOperador(userId, id);
    }

    @GetMapping("/{id}/periodos/activo")
    public ResponseEntity<Periodo> periodoActivo(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return periodoService.obtenerActivo(userId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/periodos")
    public Periodo crearPeriodo(@PathVariable Long id, @RequestBody Periodo periodo, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return periodoService.crear(userId, id, periodo);
    }

    @PutMapping("/periodos/{id}")
    public ResponseEntity<Periodo> actualizarPeriodo(@PathVariable Long id, @RequestBody Periodo periodo) {
        return ResponseEntity.ok(periodoService.actualizar(id, periodo));
    }

    @DeleteMapping("/periodos/{id}")
    public ResponseEntity<Void> eliminarPeriodo(@PathVariable Long id) {
        periodoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
