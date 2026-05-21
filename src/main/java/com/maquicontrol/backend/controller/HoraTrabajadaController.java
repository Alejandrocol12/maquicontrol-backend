package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.HoraTrabajada;
import com.maquicontrol.backend.service.HoraTrabajadaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/horas")
@CrossOrigin(origins = "*")
public class HoraTrabajadaController {

    @Autowired
    private HoraTrabajadaService horaService;

    @GetMapping
    public List<HoraTrabajada> obtenerTodas() {
        return horaService.obtenerTodas();
    }

    @GetMapping("/operador/{id}")
    public List<HoraTrabajada> obtenerPorOperadorId(@PathVariable Long id) {
        return horaService.obtenerPorOperadorId(id);
    }

    @GetMapping("/operador/nombre/{nombre}")
    public List<HoraTrabajada> obtenerPorOperador(@PathVariable String nombre) {
        return horaService.obtenerPorOperador(nombre);
    }

    @GetMapping("/maquina/{nombre}")
    public List<HoraTrabajada> obtenerPorMaquina(@PathVariable String nombre) {
        return horaService.obtenerPorMaquina(nombre);
    }

    @PostMapping
    public HoraTrabajada registrar(@RequestBody HoraTrabajada hora) {
        return horaService.guardar(hora);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        horaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}