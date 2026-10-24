package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Gasto;
import com.maquicontrol.backend.repository.GastoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.maquicontrol.backend.service.GastoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/gastos")
public class GastoController {

    @Autowired
    private GastoService gastoService;

    @Autowired
    private GastoRepository gastoRepository;

    // Sin parámetros devuelve todo. Con size (y page desde 0) devuelve una página, la más reciente primero,
    // y el total en la cabecera X-Total-Count.
    @GetMapping
    public ResponseEntity<List<Gasto>> obtenerTodos(Authentication auth,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        Long userId = (Long) auth.getPrincipal();
        if (size == null) return ResponseEntity.ok(gastoService.obtenerTodos(userId));
        var pagina = gastoRepository.findByUsuarioId(userId, PageRequest.of(Math.max(page == null ? 0 : page, 0),
                Math.min(Math.max(size, 1), 500), Sort.by(Sort.Direction.DESC, "fecha").and(Sort.by(Sort.Direction.DESC, "id"))));
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(pagina.getTotalElements())).body(pagina.getContent());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Gasto> obtenerPorId(@PathVariable Long id) {
        return gastoService.obtenerPorId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/maquina/{nombre}")
    public List<Gasto> obtenerPorMaquina(@PathVariable String nombre, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return gastoService.obtenerPorMaquina(userId, nombre);
    }

    @PostMapping
    public Gasto registrar(@RequestBody Gasto gasto, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return gastoService.guardar(userId, gasto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Gasto> actualizar(@PathVariable Long id, @RequestBody Gasto gasto, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Gasto> existente = gastoService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(gastoService.actualizar(id, gasto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Optional<Gasto> existente = gastoService.obtenerPorId(id);
        if (existente.isEmpty() || !userId.equals(existente.get().getUsuarioId())) {
            return ResponseEntity.status(403).build();
        }
        gastoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/factura")
    public ResponseEntity<Void> subirFactura(@PathVariable Long id,
                                              @RequestParam("file") MultipartFile file) throws IOException {
        gastoService.guardarFactura(id, file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/factura")
    public ResponseEntity<byte[]> descargarFactura(@PathVariable Long id) {
        return gastoService.obtenerPorId(id)
            .filter(g -> g.getFacturaPdf() != null && g.getFacturaPdf().length > 0)
            .map(g -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, detectarTipo(g.getFacturaNombre()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + g.getFacturaNombre() + "\"")
                .body(g.getFacturaPdf()))
            .orElse(ResponseEntity.notFound().build());
    }

    private static String detectarTipo(String nombre) {
        if (nombre == null) return "application/pdf";
        String n = nombre.toLowerCase();
        if (n.endsWith(".png"))              return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp"))             return "image/webp";
        if (n.endsWith(".gif"))              return "image/gif";
        return "application/pdf";
    }

    @DeleteMapping("/{id}/factura")
    public ResponseEntity<Void> eliminarFactura(@PathVariable Long id) {
        gastoService.eliminarFactura(id);
        return ResponseEntity.noContent().build();
    }
}
