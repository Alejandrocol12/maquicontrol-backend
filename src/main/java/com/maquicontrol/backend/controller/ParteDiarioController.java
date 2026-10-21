package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.ParteDiario;
import com.maquicontrol.backend.repository.ParteDiarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/partes-diarios")
public class ParteDiarioController {

    private static final Set<String> ESTADOS = Set.of("NORMAL", "LLUVIA", "MANTENIMIENTO", "DESCANSO");

    @Autowired
    private ParteDiarioRepository repo;

    @GetMapping("/maquina/{nombre}")
    public List<ParteDiario> deMaquina(@PathVariable String nombre, Authentication auth) {
        return repo.findByUsuarioIdAndMaquinaNombre((Long) auth.getPrincipal(), nombre);
    }

    // Crea o reemplaza las anotaciones del día de una máquina
    @PutMapping
    public ResponseEntity<ParteDiario> guardar(@RequestBody ParteDiario datos, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (datos.getMaquinaNombre() == null || datos.getFecha() == null) return ResponseEntity.badRequest().build();
        String estado = datos.getEstadoOperacion() == null ? "NORMAL" : datos.getEstadoOperacion();
        if (!ESTADOS.contains(estado)) return ResponseEntity.badRequest().build();

        ParteDiario parte = repo.findByUsuarioIdAndMaquinaNombreAndFecha(userId, datos.getMaquinaNombre(), datos.getFecha())
            .orElseGet(ParteDiario::new);
        parte.setUsuarioId(userId);
        parte.setMaquinaNombre(datos.getMaquinaNombre());
        parte.setFecha(datos.getFecha());
        parte.setEstadoOperacion(estado);
        String act = datos.getActividad();
        parte.setActividad(act != null && act.length() > 300 ? act.substring(0, 300) : act);
        String obs = datos.getObservaciones();
        parte.setObservaciones(obs != null && obs.length() > 1000 ? obs.substring(0, 1000) : obs);
        return ResponseEntity.ok(repo.save(parte));
    }
}
