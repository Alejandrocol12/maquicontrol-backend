package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Operador;
import com.maquicontrol.backend.repository.HoraTrabajadaRepository;
import com.maquicontrol.backend.repository.MaquinaRepository;
import com.maquicontrol.backend.repository.NovedadRepository;
import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.repository.PeriodoRepository;
import com.maquicontrol.backend.repository.SalarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OperadorService {

    @Autowired private OperadorRepository operadorRepository;
    @Autowired private PeriodoRepository periodoRepository;
    @Autowired private HoraTrabajadaRepository horaRepository;
    @Autowired private SalarioRepository salarioRepository;
    @Autowired private MaquinaRepository maquinaRepository;
    @Autowired private NovedadRepository novedadRepository;

    public List<Operador> obtenerTodos(Long userId) {
        return operadorRepository.findByUsuarioId(userId);
    }

    public Optional<Operador> obtenerPorId(Long id) {
        return operadorRepository.findById(id);
    }

    public Operador guardar(Long userId, Operador operador) {
        operador.setUsuarioId(userId);
        return operadorRepository.save(operador);
    }

    @Transactional
    public Operador actualizar(Long id, Operador datos) {
        Operador op = operadorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Operador no encontrado"));

        String nombreViejo = op.getNombre();
        String nombreNuevo = datos.getNombre();

        op.setNombre(nombreNuevo);
        op.setCedula(datos.getCedula());
        op.setTelefono(datos.getTelefono());
        op.setEmail(datos.getEmail());
        op.setObservaciones(datos.getObservaciones());
        op.setActivo(datos.isActivo());
        Operador saved = operadorRepository.save(op);

        // Las horas, liquidaciones, máquina asignada y novedades quedan enlazadas al
        // operador por su nombre (no solo por id) — sin esta cascada, renombrar un
        // operador los deja huérfanos y parece que se borraron.
        if (nombreViejo != null && !nombreViejo.equals(nombreNuevo)) {
            Long userId = op.getUsuarioId();
            maquinaRepository.actualizarNombreOperador(userId, nombreViejo, nombreNuevo);
            horaRepository.actualizarNombreOperador(userId, nombreViejo, nombreNuevo);
            salarioRepository.actualizarNombreOperador(userId, nombreViejo, nombreNuevo);
            novedadRepository.actualizarNombreOperador(userId, nombreViejo, nombreNuevo);
        }

        return saved;
    }

    // Nombres de operador que aparecen en horas/liquidaciones/máquinas/novedades pero
    // ya no coinciden con ningún operador actual — huella de un renombre hecho antes
    // de que existiera la cascada en actualizar(), o de un operador ya eliminado.
    public List<String> nombresHuerfanos(Long userId) {
        Set<String> actuales = operadorRepository.findByUsuarioId(userId).stream()
                .map(Operador::getNombre)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toSet());

        Set<String> referenciados = new HashSet<>();
        maquinaRepository.findByUsuarioId(userId).forEach(m -> {
            if (m.getOperadorNombre() != null && !m.getOperadorNombre().isBlank()) referenciados.add(m.getOperadorNombre());
        });
        horaRepository.findByUsuarioId(userId).forEach(h -> {
            if (h.getOperadorNombre() != null && !h.getOperadorNombre().isBlank()) referenciados.add(h.getOperadorNombre());
        });
        salarioRepository.findByUsuarioId(userId).forEach(s -> {
            if (s.getOperadorNombre() != null && !s.getOperadorNombre().isBlank()) referenciados.add(s.getOperadorNombre());
        });
        novedadRepository.findByUsuarioIdOrderByFechaDesc(userId).forEach(n -> {
            if (n.getOperadorNombre() != null && !n.getOperadorNombre().isBlank()) referenciados.add(n.getOperadorNombre());
        });

        referenciados.removeAll(actuales);
        return new java.util.ArrayList<>(referenciados);
    }

    // Reconecta manualmente horas/liquidaciones/máquina/novedades que quedaron con un
    // nombre viejo (de antes de la cascada) hacia el operador actual.
    @Transactional
    public void repararNombre(Long id, String nombreAnterior) {
        Operador op = operadorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Operador no encontrado"));
        if (nombreAnterior == null || nombreAnterior.isBlank() || nombreAnterior.equals(op.getNombre())) return;

        Long userId = op.getUsuarioId();
        maquinaRepository.actualizarNombreOperador(userId, nombreAnterior, op.getNombre());
        horaRepository.actualizarNombreOperador(userId, nombreAnterior, op.getNombre());
        salarioRepository.actualizarNombreOperador(userId, nombreAnterior, op.getNombre());
        novedadRepository.actualizarNombreOperador(userId, nombreAnterior, op.getNombre());
    }

    @Transactional
    public void eliminar(Long id) {
        operadorRepository.findById(id).ifPresent(op -> {
            periodoRepository.deleteAll(periodoRepository.findByOperadorId(id));
            horaRepository.deleteByOperadorNombre(op.getNombre());
            salarioRepository.deleteByOperadorNombre(op.getNombre());
            operadorRepository.deleteById(id);
        });
    }
}
