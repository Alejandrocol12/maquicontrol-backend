package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Corte;
import com.maquicontrol.backend.model.Faena;
import com.maquicontrol.backend.model.Ingreso;
import com.maquicontrol.backend.model.PagoCliente;
import com.maquicontrol.backend.repository.CorteRepository;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.IngresoRepository;
import com.maquicontrol.backend.repository.PagoClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class CorteService {

    @Autowired private CorteRepository corteRepository;
    @Autowired private FaenaRepository faenaRepository;
    @Autowired private IngresoRepository ingresoRepository;
    @Autowired private PagoClienteRepository pagoClienteRepository;
    @Autowired private PagoClienteService pagoClienteService;

    public List<Corte> obtenerTodos(Long userId) {
        return corteRepository.findByUsuarioId(userId);
    }

    // IllegalArgumentException = datos mal digitados (400); IllegalStateException = choca con
    // lo que ya existe (409).
    @Transactional
    public Corte crear(Long userId, Long faenaId, LocalDate inicio, LocalDate fin, boolean crearCobro, boolean conOperador) {
        if (faenaId == null || inicio == null || fin == null) throw new IllegalArgumentException("Faltan las fechas del corte");
        if (inicio.isAfter(fin)) throw new IllegalArgumentException("La fecha \"desde\" no puede ser después de \"hasta\"");

        Faena faena = faenaRepository.findById(faenaId)
            .filter(f -> userId.equals(f.getUsuarioId()))
            .orElseThrow(() -> new IllegalArgumentException("Periodo no encontrado"));

        List<Corte> existentes = corteRepository.findByFaenaId(faenaId);
        boolean seCruza = existentes.stream()
            .anyMatch(c -> !inicio.isAfter(c.getFechaFin()) && !fin.isBefore(c.getFechaInicio()));
        if (seCruza) throw new IllegalStateException("Esas fechas se cruzan con un corte que ya existe");

        List<Ingreso> delTramo = ingresoRepository.findByFaenaId(faenaId).stream()
            .filter(i -> i.getFecha() != null && !i.getFecha().isBefore(inicio) && !i.getFecha().isAfter(fin))
            .toList();
        if (delTramo.isEmpty()) throw new IllegalArgumentException("No hay trabajos registrados en esas fechas");

        double horas = delTramo.stream()
            .filter(i -> "Horas".equals(i.getTipoTrabajo()))
            .mapToDouble(Ingreso::getCantidad).sum();
        double total = delTramo.stream().mapToDouble(Ingreso::getTotal).sum();

        Corte corte = new Corte();
        corte.setUsuarioId(userId);
        corte.setFaenaId(faenaId);
        corte.setMaquinaNombre(faena.getMaquinaNombre());
        corte.setFechaInicio(inicio);
        corte.setFechaFin(fin);
        corte.setHoras(horas);
        corte.setTotal(total);
        corte.setConOperador(conOperador);

        if (crearCobro) {
            int numero = (int) existentes.stream().filter(c -> c.getFechaInicio().isBefore(inicio)).count() + 1;
            String cliente = faena.getCliente() != null && !faena.getCliente().isBlank()
                ? faena.getCliente() : faena.getNombreObra();
            PagoCliente pago = new PagoCliente();
            pago.setCliente(cliente != null && !cliente.isBlank() ? cliente : "Cliente sin nombre");
            pago.setMaquinaNombre(faena.getMaquinaNombre());
            pago.setDescripcion("Corte " + numero + " · " + fechaCorta(inicio) + " a " + fechaCorta(fin) + " · " + formatoHoras(horas) + " h");
            pago.setValorTotal(total);
            pago.setValorPagado(0);
            pago.setFecha(fin);
            corte.setPagoClienteId(pagoClienteService.guardar(userId, pago).getId());
        }
        return corteRepository.save(corte);
    }

    // Solo se puede deshacer el ultimo corte del periodo, para no dejar huecos en la mitad.
    // El cobro que se creo con el corte se borra solo si todavia no tiene abonos.
    @Transactional
    public void eliminar(Long userId, Long id) {
        Corte corte = corteRepository.findById(id)
            .filter(c -> userId.equals(c.getUsuarioId()))
            .orElseThrow(() -> new IllegalArgumentException("Corte no encontrado"));

        Corte ultimo = corteRepository.findByFaenaId(corte.getFaenaId()).stream()
            .max(Comparator.comparing(Corte::getFechaFin)).orElse(corte);
        if (!ultimo.getId().equals(corte.getId())) {
            throw new IllegalStateException("Solo se puede deshacer el último corte del periodo");
        }

        if (corte.getPagoClienteId() != null) {
            pagoClienteRepository.findById(corte.getPagoClienteId())
                .filter(p -> p.getValorPagado() <= 0)
                .ifPresent(pagoClienteRepository::delete);
        }
        corteRepository.delete(corte);
    }

    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};

    private String fechaCorta(LocalDate d) {
        return d.getDayOfMonth() + " " + MESES[d.getMonthValue() - 1] + " " + d.getYear();
    }

    private String formatoHoras(double h) {
        double r = Math.round(h * 10) / 10.0;
        return (r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r)).replace('.', ',');
    }
}
