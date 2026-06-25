package com.maquicontrol.backend.controller;

import com.maquicontrol.backend.model.*;
import com.maquicontrol.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class EnlaceCompartidoController {

    @Autowired private EnlaceCompartidoRepository enlaceRepo;
    @Autowired private MaquinaRepository maquinaRepo;
    @Autowired private FaenaRepository faenaRepo;
    @Autowired private IngresoRepository ingresoRepo;
    @Autowired private GastoRepository gastoRepo;
    @Autowired private MantenimientoRepository mantenimientoRepo;

    @PostMapping("/api/compartido")
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> body, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        Long userId = (Long) auth.getPrincipal();
        Long maquinaId = ((Number) body.get("maquinaId")).longValue();
        String nombre = body.getOrDefault("nombre", "Enlace compartido").toString();

        return maquinaRepo.findById(maquinaId).map(m -> {
            EnlaceCompartido enlace = new EnlaceCompartido();
            enlace.setToken(UUID.randomUUID().toString().replace("-", ""));
            enlace.setUsuarioId(userId);
            enlace.setMaquinaId(maquinaId);
            enlace.setMaquinaNombre(m.getNombre());
            enlace.setNombre(nombre);
            enlace.setActivo(true);
            enlace.setCreadoEn(LocalDateTime.now());
            return ResponseEntity.ok(enlaceRepo.save(enlace));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/compartido")
    public ResponseEntity<?> listar(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(enlaceRepo.findByUsuarioIdAndActivoTrue(userId));
    }

    @DeleteMapping("/api/compartido/{token}")
    public ResponseEntity<?> revocar(@PathVariable String token, Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        Long userId = (Long) auth.getPrincipal();
        return enlaceRepo.findByToken(token)
            .filter(e -> e.getUsuarioId().equals(userId))
            .map(e -> {
                e.setActivo(false);
                enlaceRepo.save(e);
                return ResponseEntity.ok().build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/publico/{token}")
    public ResponseEntity<?> verPublico(@PathVariable String token) {
        Optional<EnlaceCompartido> opt = enlaceRepo.findByTokenAndActivoTrue(token);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        EnlaceCompartido enlace = opt.get();

        Optional<Maquina> maquinaOpt = maquinaRepo.findById(enlace.getMaquinaId());
        if (maquinaOpt.isEmpty()) return ResponseEntity.notFound().build();
        Maquina m = maquinaOpt.get();

        List<Faena> faenas = faenaRepo.findByUsuarioIdAndMaquinaNombre(
            enlace.getUsuarioId(), enlace.getMaquinaNombre()
        );

        List<Map<String, Object>> faenasDto = faenas.stream()
            .sorted(Comparator.comparing(
                f -> f.getFechaInicio() != null ? f.getFechaInicio().toString() : "",
                Comparator.reverseOrder()
            ))
            .map(f -> {
                double ing, gas, util;
                if ("cerrada".equals(f.getEstado())) {
                    ing  = f.getTotalIngresos();
                    gas  = f.getTotalGastos();
                    util = f.getUtilidadNeta();
                } else {
                    ing  = ingresoRepo.findByFaenaId(f.getId()).stream().mapToDouble(Ingreso::getTotal).sum();
                    gas  = gastoRepo.findByFaenaId(f.getId()).stream().mapToDouble(Gasto::getMonto).sum();
                    util = ing - gas;
                }
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id",          f.getId());
                dto.put("nombreObra",  f.getNombreObra());
                dto.put("cliente",     f.getCliente());
                dto.put("fechaInicio", f.getFechaInicio());
                dto.put("fechaFin",    f.getFechaFin());
                dto.put("estado",      f.getEstado());
                dto.put("totalIngresos", ing);
                dto.put("totalGastos",   gas);
                dto.put("utilidadNeta",  util);
                return dto;
            })
            .collect(Collectors.toList());

        double totalIngresos = faenasDto.stream().mapToDouble(d -> ((Number) d.get("totalIngresos")).doubleValue()).sum();
        double totalGastos   = faenasDto.stream().mapToDouble(d -> ((Number) d.get("totalGastos")).doubleValue()).sum();

        List<Map<String, Object>> mants = mantenimientoRepo
            .findByUsuarioIdAndMaquinaNombre(enlace.getUsuarioId(), enlace.getMaquinaNombre())
            .stream()
            .sorted(Comparator.comparing(
                mt -> mt.getFecha() != null ? mt.getFecha().toString() : "",
                Comparator.reverseOrder()
            ))
            .limit(5)
            .map(mt -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("fecha",       mt.getFecha());
                d.put("tipo",        mt.getTipo());
                d.put("descripcion", mt.getDescripcion());
                d.put("costo",       mt.getCosto());
                d.put("horometro",   mt.getHorometro());
                return d;
            })
            .collect(Collectors.toList());

        Map<String, Object> maquinaDto = new LinkedHashMap<>();
        maquinaDto.put("nombre",          m.getNombre());
        maquinaDto.put("tipo",            m.getTipo());
        maquinaDto.put("placa",           m.getPlaca());
        maquinaDto.put("estado",          m.getEstado());
        maquinaDto.put("horometroActual", m.getHorometroActual());

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("totalIngresos",  totalIngresos);
        resumen.put("totalGastos",    totalGastos);
        resumen.put("utilidadNeta",   totalIngresos - totalGastos);
        resumen.put("totalFaenas",    faenas.size());

        List<Map<String, Object>> gastosDto = gastoRepo
            .findByUsuarioIdAndMaquinaNombre(enlace.getUsuarioId(), enlace.getMaquinaNombre())
            .stream()
            .sorted(Comparator.comparing(
                g -> g.getFecha() != null ? g.getFecha().toString() : "",
                Comparator.reverseOrder()
            ))
            .map(g -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("fecha",       g.getFecha());
                d.put("descripcion", g.getDescripcion());
                d.put("categoria",   g.getCategoria());
                d.put("monto",       g.getMonto());
                return d;
            })
            .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("nombre",         enlace.getNombre());
        response.put("maquina",        maquinaDto);
        response.put("resumen",        resumen);
        response.put("faenas",         faenasDto);
        response.put("gastos",         gastosDto);
        response.put("mantenimientos", mants);

        return ResponseEntity.ok(response);
    }
}
