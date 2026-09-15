package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Ingreso;
import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.HoraTrabajadaRepository;
import com.maquicontrol.backend.repository.IngresoRepository;
import com.maquicontrol.backend.repository.MaquinaRepository;
import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class IngresoService {

    @Autowired private IngresoRepository ingresoRepository;
    @Autowired private FaenaRepository faenaRepository;
    @Autowired private MaquinaRepository maquinaRepository;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private OperadorRepository operadorRepo;
    @Autowired private FaenaService faenaService;
    @Autowired private HoraTrabajadaRepository horaTrabajadaRepository;

    // Si userId pertenece a un operador, devuelve el userId del admin dueño
    private Long resolverAdminId(Long userId) {
        if (userId == null) return null;
        return usuarioRepo.findById(userId)
            .filter(u -> u.getOperadorId() != null)
            .flatMap(u -> operadorRepo.findById(u.getOperadorId()))
            .map(op -> op.getUsuarioId())
            .orElse(userId);
    }

    public List<Ingreso> obtenerTodos(Long userId) {
        return ingresoRepository.findByUsuarioId(userId);
    }

    public Optional<Ingreso> obtenerPorId(Long id) {
        return ingresoRepository.findById(id);
    }

    public List<Ingreso> obtenerPorMaquina(Long userId, String maquinaNombre) {
        return ingresoRepository.findByUsuarioIdAndMaquinaNombre(userId, maquinaNombre);
    }

    @Transactional
    public Ingreso guardar(Long userId, Ingreso ingreso) {
        Long adminId = resolverAdminId(userId);
        ingreso.setUsuarioId(adminId);
        ingreso.setTotal(ingreso.getCantidad() * ingreso.getValorUnitario());
        if (ingreso.getMaquinaNombre() != null && ingreso.getFaenaId() == null) {
            faenaRepository.findByUsuarioIdAndMaquinaNombreAndEstado(adminId, ingreso.getMaquinaNombre(), "activa")
                .ifPresent(f -> ingreso.setFaenaId(f.getId()));
        }
        Ingreso saved = ingresoRepository.save(ingreso);
        faenaService.recalcularTotalesSiCerrada(saved.getFaenaId());
        return saved;
    }

    @Transactional
    public Ingreso actualizar(Long id, Ingreso ingresoActualizado) {
        Ingreso ingreso = ingresoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Ingreso no encontrado"));
        ingreso.setDescripcion(ingresoActualizado.getDescripcion());
        ingreso.setTipoTrabajo(ingresoActualizado.getTipoTrabajo());
        ingreso.setCantidad(ingresoActualizado.getCantidad());
        ingreso.setValorUnitario(ingresoActualizado.getValorUnitario());
        ingreso.setTotal(ingresoActualizado.getCantidad() * ingresoActualizado.getValorUnitario());
        ingreso.setFecha(ingresoActualizado.getFecha());
        ingreso.setMaquinaNombre(ingresoActualizado.getMaquinaNombre());
        Ingreso saved = ingresoRepository.save(ingreso);
        faenaService.recalcularTotalesSiCerrada(saved.getFaenaId());
        return saved;
    }

    // Rellena horometroInicio/horometroFin de ingresos viejos tipo "Horas" que quedaron sin ese
    // dato, únicamente cuando existe un HoraTrabajada vinculado (ingresoId) con el valor exacto ya
    // guardado -- no se estima ni se adivina nada.
    @Transactional
    public int backfillHorometroExacto(Long userId) {
        Long adminId = resolverAdminId(userId);
        int actualizados = 0;
        for (Ingreso ingreso : ingresoRepository.findByUsuarioId(adminId)) {
            if (!"Horas".equals(ingreso.getTipoTrabajo())) continue;
            if (ingreso.getHorometroInicio() != null && ingreso.getHorometroFin() != null) continue;
            Optional<com.maquicontrol.backend.model.HoraTrabajada> hora = horaTrabajadaRepository.findByIngresoId(ingreso.getId());
            if (hora.isEmpty()) continue;
            ingreso.setHorometroInicio(hora.get().getHorometroInicio());
            ingreso.setHorometroFin(hora.get().getHorometroFin());
            ingresoRepository.save(ingreso);
            actualizados++;
        }
        return actualizados;
    }

    // Estima horometroInicio/horometroFin de los ingresos "Horas" que el backfill exacto no pudo
    // resolver, encadenando hacia atrás desde el horómetro actual de cada máquina. Los registros
    // que ya tienen horómetro (exacto) se respetan y se usan como punto de anclaje de la cadena.
    // Riesgo conocido: si el horómetro de la máquina se editó alguna vez a mano, los estimados
    // anteriores a esa edición pueden quedar desfasados.
    @Transactional
    public int backfillHorometroEstimado(Long userId) {
        Long adminId = resolverAdminId(userId);
        List<Ingreso> horas = ingresoRepository.findByUsuarioId(adminId).stream()
            .filter(i -> "Horas".equals(i.getTipoTrabajo()) && i.getMaquinaNombre() != null)
            .sorted(Comparator.comparing(Ingreso::getId))
            .collect(Collectors.toList());

        Map<String, List<Ingreso>> porMaquina = horas.stream()
            .collect(Collectors.groupingBy(Ingreso::getMaquinaNombre, LinkedHashMap::new, Collectors.toList()));

        int actualizados = 0;
        for (Map.Entry<String, List<Ingreso>> entrada : porMaquina.entrySet()) {
            Optional<Maquina> maquinaOpt = maquinaRepository.findByUsuarioIdAndNombre(adminId, entrada.getKey());
            if (maquinaOpt.isEmpty()) continue;
            List<Ingreso> lista = entrada.getValue(); // ordenada por id asc = orden real de creación
            double horometroActual = maquinaOpt.get().getHorometroActual();
            for (int idx = lista.size() - 1; idx >= 0; idx--) {
                Ingreso ingreso = lista.get(idx);
                if (ingreso.getHorometroInicio() != null && ingreso.getHorometroFin() != null) {
                    horometroActual = ingreso.getHorometroInicio();
                    continue;
                }
                double fin = horometroActual;
                double inicio = fin - ingreso.getCantidad();
                ingreso.setHorometroFin(fin);
                ingreso.setHorometroInicio(inicio);
                ingresoRepository.save(ingreso);
                actualizados++;
                horometroActual = inicio;
            }
        }
        return actualizados;
    }

    @Transactional
    public void eliminar(Long id) {
        Ingreso ingreso = ingresoRepository.findById(id).orElse(null);
        if (ingreso != null && "Horas".equals(ingreso.getTipoTrabajo()) && ingreso.getMaquinaNombre() != null) {
            maquinaRepository.findByUsuarioIdAndNombre(ingreso.getUsuarioId(), ingreso.getMaquinaNombre())
                .ifPresent(maq -> {
                    maq.setHorometroActual(maq.getHorometroActual() - ingreso.getCantidad());
                    maquinaRepository.save(maq);
                });
        }
        horaTrabajadaRepository.deleteByIngresoId(id);
        ingresoRepository.deleteById(id);
        if (ingreso != null) faenaService.recalcularTotalesSiCerrada(ingreso.getFaenaId());
    }
}
