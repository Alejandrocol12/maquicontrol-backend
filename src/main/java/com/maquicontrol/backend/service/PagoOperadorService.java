package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Faena;
import com.maquicontrol.backend.model.Gasto;
import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.model.PagoOperador;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.GastoRepository;
import com.maquicontrol.backend.repository.MaquinaRepository;
import com.maquicontrol.backend.repository.PagoOperadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class PagoOperadorService {

    public static final String CATEGORIA = "Pago operador";

    @Autowired private PagoOperadorRepository pagoRepository;
    @Autowired private GastoRepository gastoRepository;
    @Autowired private MaquinaRepository maquinaRepository;
    @Autowired private FaenaRepository faenaRepository;
    @Autowired private FaenaService faenaService;

    public List<PagoOperador> obtenerTodos(Long userId) {
        return pagoRepository.findByUsuarioId(userId);
    }

    public Optional<PagoOperador> obtenerPorId(Long id) {
        return pagoRepository.findById(id);
    }

    public List<PagoOperador> obtenerPorOperador(Long userId, String operadorNombre) {
        return pagoRepository.findByUsuarioIdAndOperadorNombre(userId, operadorNombre);
    }

    @Transactional
    public PagoOperador guardar(Long userId, PagoOperador pago) {
        pago.setUsuarioId(userId);
        pago.setGastoGeneradoId(null);
        if (pago.getFecha() == null) pago.setFecha(LocalDate.now());
        PagoOperador saved = pagoRepository.save(pago);
        return crearGasto(saved);
    }

    @Transactional
    public PagoOperador actualizar(Long id, PagoOperador pagoActualizado) {
        PagoOperador pago = pagoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Pago no encontrado"));
        pago.setOperadorNombre(pagoActualizado.getOperadorNombre());
        pago.setDescripcion(pagoActualizado.getDescripcion());
        pago.setMonto(pagoActualizado.getMonto());
        pago.setFecha(pagoActualizado.getFecha());
        PagoOperador saved = pagoRepository.save(pago);

        // Si el pago ya tenia su gasto, se actualiza el mismo; si era un pago viejo que
        // todavia no se habia pasado a Gastos, se deja como estaba.
        if (saved.getGastoGeneradoId() != null) {
            Optional<Gasto> gastoOpt = gastoRepository.findById(saved.getGastoGeneradoId());
            if (gastoOpt.isPresent()) {
                Gasto g = gastoOpt.get();
                Long faenaAnterior = g.getFaenaId();
                llenarGasto(g, saved);
                gastoRepository.save(g);
                faenaService.recalcularTotalesSiCerrada(faenaAnterior);
                if (g.getFaenaId() != null && !g.getFaenaId().equals(faenaAnterior)) {
                    faenaService.recalcularTotalesSiCerrada(g.getFaenaId());
                }
            } else {
                return crearGasto(saved);
            }
        }
        return saved;
    }

    @Transactional
    public void eliminar(Long id) {
        pagoRepository.findById(id).ifPresent(pago -> {
            if (pago.getGastoGeneradoId() != null) {
                gastoRepository.findById(pago.getGastoGeneradoId()).ifPresent(g -> {
                    gastoRepository.delete(g);
                    faenaService.recalcularTotalesSiCerrada(g.getFaenaId());
                });
            }
            pagoRepository.delete(pago);
        });
    }

    // Pagos anotados cuando Pago Operador era solo informativo: todavia no tienen gasto.
    public List<PagoOperador> sinGasto(Long userId) {
        return pagoRepository.findByUsuarioId(userId).stream()
            .filter(p -> p.getGastoGeneradoId() == null)
            .sorted(Comparator.comparing(PagoOperador::getFecha, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    // Pasa a Gastos, con su fecha original, los pagos que aun no tienen gasto. Devuelve cuantos paso.
    @Transactional
    public int pasarAGastos(Long userId) {
        List<PagoOperador> pendientes = sinGasto(userId);
        pendientes.forEach(this::crearGasto);
        return pendientes.size();
    }

    private PagoOperador crearGasto(PagoOperador pago) {
        Gasto g = new Gasto();
        g.setUsuarioId(pago.getUsuarioId());
        llenarGasto(g, pago);
        Gasto gastoSaved = gastoRepository.save(g);
        pago.setGastoGeneradoId(gastoSaved.getId());
        PagoOperador saved = pagoRepository.save(pago);
        faenaService.recalcularTotalesSiCerrada(gastoSaved.getFaenaId());
        return saved;
    }

    // El gasto queda en la maquina que maneja el operador y en el periodo de esa maquina que
    // cubre la fecha del pago, para que la utilidad del periodo descuente lo pagado.
    private void llenarGasto(Gasto g, PagoOperador pago) {
        String desc = pago.getDescripcion() != null && !pago.getDescripcion().isBlank()
            ? "Pago operador — " + pago.getOperadorNombre() + ": " + pago.getDescripcion().trim()
            : "Pago operador — " + pago.getOperadorNombre();
        g.setDescripcion(desc);
        g.setCategoria(CATEGORIA);
        g.setMonto(pago.getMonto());
        g.setFecha(pago.getFecha());

        String maquina = maquinaRepository.findByUsuarioIdAndOperadorNombre(pago.getUsuarioId(), pago.getOperadorNombre())
            .stream().map(Maquina::getNombre).findFirst().orElse(null);
        g.setMaquinaNombre(maquina);
        g.setFaenaId(maquina == null ? null : faenaDeLaFecha(pago.getUsuarioId(), maquina, pago.getFecha()));
    }

    private Long faenaDeLaFecha(Long userId, String maquina, LocalDate fecha) {
        List<Faena> faenas = faenaRepository.findByUsuarioIdAndMaquinaNombre(userId, maquina);
        if (fecha != null) {
            Optional<Faena> cubre = faenas.stream()
                .filter(f -> f.getFechaInicio() != null && !fecha.isBefore(f.getFechaInicio())
                    && (f.getFechaFin() == null || "activa".equals(f.getEstado()) || !fecha.isAfter(f.getFechaFin())))
                .max(Comparator.comparing(Faena::getFechaInicio));
            if (cubre.isPresent()) return cubre.get().getId();
        }
        return faenas.stream().filter(f -> "activa".equals(f.getEstado())).map(Faena::getId).findFirst().orElse(null);
    }
}
