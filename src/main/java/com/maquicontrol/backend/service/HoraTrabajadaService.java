package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Faena;
import com.maquicontrol.backend.model.HoraTrabajada;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.HoraTrabajadaRepository;
import com.maquicontrol.backend.repository.MaquinaRepository;
import com.maquicontrol.backend.repository.OperadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

@Service
public class HoraTrabajadaService {

    @Autowired private HoraTrabajadaRepository horaRepository;
    @Autowired private MaquinaRepository maquinaRepository;
    @Autowired private OperadorRepository operadorRepository;
    @Autowired private FaenaRepository faenaRepository;

    public List<HoraTrabajada> obtenerTodas(Long userId) {
        return horaRepository.findByUsuarioId(userId);
    }

    public Optional<HoraTrabajada> obtenerPorId(Long id) {
        return horaRepository.findById(id);
    }

    public List<HoraTrabajada> obtenerPorOperadorId(Long userId, Long operadorId) {
        String nombre = operadorRepository.findById(operadorId)
                .map(o -> o.getNombre()).orElse("");
        return horaRepository.findByUsuarioIdAndOperadorIdOrNombre(userId, operadorId, nombre);
    }

    public List<HoraTrabajada> obtenerPorOperador(String operadorNombre) {
        return horaRepository.findByOperadorNombre(operadorNombre);
    }

    public List<HoraTrabajada> obtenerPorMaquina(Long userId, String maquinaNombre) {
        return horaRepository.findByUsuarioIdAndMaquinaNombre(userId, maquinaNombre);
    }

    @Transactional
    public HoraTrabajada guardar(Long userId, HoraTrabajada hora) {
        hora.setUsuarioId(userId);
        hora.setHorometroFin(hora.getHorometroInicio() + hora.getHoras());

        // Auto-asociar a faena activa; si no existe, crea una automáticamente
        if (hora.getFaenaId() == null && hora.getMaquinaNombre() != null) {
            Optional<Faena> faenaOpt = faenaRepository
                .findByUsuarioIdAndMaquinaNombreAndEstado(userId, hora.getMaquinaNombre(), "activa");
            if (faenaOpt.isPresent()) {
                hora.setFaenaId(faenaOpt.get().getId());
            } else {
                Faena nueva = new Faena();
                nueva.setUsuarioId(userId);
                nueva.setMaquinaNombre(hora.getMaquinaNombre());
                nueva.setNombreObra("Periodo auto — " + hora.getMaquinaNombre());
                nueva.setEstado("activa");
                nueva.setFechaInicio(hora.getFecha() != null ? hora.getFecha() : LocalDate.now());
                Faena savedFaena = faenaRepository.save(nueva);
                hora.setFaenaId(savedFaena.getId());
            }
        }

        HoraTrabajada saved = horaRepository.save(hora);

        // Actualizar horómetro de la máquina solo si el nuevo valor es mayor
        maquinaRepository.findByUsuarioId(userId).stream()
            .filter(m -> m.getNombre().equals(hora.getMaquinaNombre()))
            .findFirst()
            .ifPresent(m -> {
                if (hora.getHorometroFin() > m.getHorometroActual()) {
                    m.setHorometroActual(hora.getHorometroFin());
                    maquinaRepository.save(m);
                }
            });

        return saved;
    }

    @Transactional
    public void eliminar(Long id) {
        horaRepository.findById(id).ifPresent(hora -> {
            Long userId = hora.getUsuarioId();
            String maqNombre = hora.getMaquinaNombre();

            horaRepository.deleteById(id);

            // Recalcular horómetro con el máximo de los registros restantes
            if (maqNombre != null && userId != null) {
                List<HoraTrabajada> restantes = horaRepository.findByUsuarioIdAndMaquinaNombre(userId, maqNombre);
                OptionalDouble maxHoro = restantes.stream()
                    .mapToDouble(h -> h.getHorometroFin())
                    .max();
                maquinaRepository.findByUsuarioId(userId).stream()
                    .filter(m -> m.getNombre().equals(maqNombre))
                    .findFirst()
                    .ifPresent(m -> {
                        m.setHorometroActual(maxHoro.orElse(0.0));
                        maquinaRepository.save(m);
                    });
            }
        });
    }
}
