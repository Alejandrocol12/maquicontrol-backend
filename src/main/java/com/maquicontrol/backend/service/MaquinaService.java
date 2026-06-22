package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class MaquinaService {

    @Autowired private MaquinaRepository maquinaRepository;
    @Autowired private IngresoRepository ingresoRepository;
    @Autowired private GastoRepository gastoRepository;
    @Autowired private CombustibleRepository combustibleRepository;
    @Autowired private MantenimientoRepository mantenimientoRepository;
    @Autowired private HoraTrabajadaRepository horaRepository;
    @Autowired private SalarioRepository salarioRepository;
    @Autowired private FaenaRepository faenaRepository;

    public List<Maquina> obtenerTodas(Long userId) {
        return maquinaRepository.findByUsuarioId(userId);
    }

    public Optional<Maquina> obtenerPorId(Long id) {
        return maquinaRepository.findById(id);
    }

    public Maquina guardar(Long userId, Maquina maquina) {
        maquina.setUsuarioId(userId);
        return maquinaRepository.save(maquina);
    }

    @Transactional
    public Maquina actualizar(Long id, Maquina maquinaActualizada) {
        Maquina maquina = maquinaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Máquina no encontrada"));

        String nombreViejo = maquina.getNombre();
        String nombreNuevo = maquinaActualizada.getNombre();

        maquina.setNombre(nombreNuevo);
        maquina.setTipo(maquinaActualizada.getTipo());
        maquina.setPlaca(maquinaActualizada.getPlaca());
        maquina.setHorometroActual(maquinaActualizada.getHorometroActual());
        maquina.setEstado(maquinaActualizada.getEstado());
        maquina.setOperadorNombre(maquinaActualizada.getOperadorNombre());
        maquina.setValorHoraOperador(maquinaActualizada.getValorHoraOperador());
        maquina.setValorHoraMaquina(maquinaActualizada.getValorHoraMaquina());
        Maquina saved = maquinaRepository.save(maquina);

        // #7: si el nombre cambió, cascadear la actualización a todos los registros relacionados
        if (nombreViejo != null && !nombreViejo.equals(nombreNuevo)) {
            Long userId = maquina.getUsuarioId();
            ingresoRepository.actualizarNombreMaquina(userId, nombreViejo, nombreNuevo);
            gastoRepository.actualizarNombreMaquina(userId, nombreViejo, nombreNuevo);
            combustibleRepository.actualizarNombreMaquina(userId, nombreViejo, nombreNuevo);
            mantenimientoRepository.actualizarNombreMaquina(userId, nombreViejo, nombreNuevo);
            horaRepository.actualizarNombreMaquina(userId, nombreViejo, nombreNuevo);
            salarioRepository.actualizarNombreMaquina(userId, nombreViejo, nombreNuevo);
            faenaRepository.actualizarNombreMaquina(userId, nombreViejo, nombreNuevo);
        }

        return saved;
    }

    @Transactional
    public void eliminar(Long id, Long userId) {
        maquinaRepository.findById(id).ifPresent(maq -> {
            String nombre = maq.getNombre();
            ingresoRepository.deleteByUsuarioIdAndMaquinaNombre(userId, nombre);
            gastoRepository.deleteByUsuarioIdAndMaquinaNombre(userId, nombre);
            combustibleRepository.deleteByUsuarioIdAndMaquinaNombre(userId, nombre);
            mantenimientoRepository.deleteByUsuarioIdAndMaquinaNombre(userId, nombre);
            horaRepository.deleteByUsuarioIdAndMaquinaNombre(userId, nombre);
            salarioRepository.deleteByMaquinaNombre(nombre);
            faenaRepository.deleteAll(faenaRepository.findByUsuarioIdAndMaquinaNombre(userId, nombre));
            maquinaRepository.deleteById(id);
        });
    }
}
