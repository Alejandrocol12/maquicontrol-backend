package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.Gasto;
import com.maquicontrol.backend.service.GastoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/gastos")
@CrossOrigin(origins = "*")
public class GastoController {

    @Autowired
    private GastoService gastoService;

    @GetMapping
    public List<Gasto> obtenerTodos(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return gastoService.obtenerTodos(userId);
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
    public ResponseEntity<Gasto> actualizar(@PathVariable Long id, @RequestBody Gasto gasto) {
        return ResponseEntity.ok(gastoService.actualizar(id, gasto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
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
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + g.getFacturaNombre() + "\"")
                .body(g.getFacturaPdf()))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/factura")
    public ResponseEntity<Void> eliminarFactura(@PathVariable Long id) {
        gastoService.eliminarFactura(id);
        return ResponseEntity.noContent().build();
    }
}
