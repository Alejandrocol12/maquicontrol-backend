package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Corte;
import com.maquicontrol.backend.model.Faena;
import com.maquicontrol.backend.model.Gasto;
import com.maquicontrol.backend.model.HoraTrabajada;
import com.maquicontrol.backend.model.PagoOperador;
import com.maquicontrol.backend.repository.GastoRepository;
import com.maquicontrol.backend.repository.HoraTrabajadaRepository;
import com.maquicontrol.backend.repository.PagoOperadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// El costo del operador se reconoce al hacer el corte (causado), no cuando se le paga.
//
// Al cortar, cada operador que trabajó en el tramo recibe un gasto "Pago operador" por lo que se ganó.
// Ese gasto lleva dos datos fijos: lo ganado en el corte y lo que ya había ganado antes de empezarlo.
// Con eso, lo pagado a un corte se calcula siempre igual que en la pantalla de Operadores: los pagos
// llenan primero lo ganado antes y después cada corte, del más viejo al más nuevo.
//
// Un pago nuevo solo crea gasto por lo que NO cae en un corte ya causado (por ejemplo un anticipo),
// así lo mismo no se cuenta dos veces. Y al cortar, lo que ya se pagó de más (anticipos que
// ya fueron gasto) se descuenta del gasto del corte por la misma razón.
@Service
public class CausacionOperadorService {

    private static final double TOLERANCIA = 0.5;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("es", "CO"));

    @Autowired private GastoRepository gastoRepository;
    @Autowired private HoraTrabajadaRepository horaRepository;
    @Autowired private PagoOperadorRepository pagoRepository;
    @Autowired private FaenaService faenaService;

    private static double ganado(List<HoraTrabajada> horas) {
        return horas.stream().mapToDouble(h -> h.getHoras() * h.getValorHora()).sum();
    }

    // Gastos de operador que se crearon al hacer un corte
    public List<Gasto> causados(Long userId, String operador) {
        return gastoRepository.findByUsuarioIdAndCorteIdIsNotNull(userId).stream()
            .filter(g -> PagoOperadorService.CATEGORIA.equals(g.getCategoria()) && operador != null && operador.equals(g.getOperadorNombre()))
            .toList();
    }

    public double pagadoTotal(Long userId, String operador, Long excluirPagoId) {
        return pagoRepository.findByUsuarioIdAndOperadorNombre(userId, operador).stream()
            .filter(p -> excluirPagoId == null || !excluirPagoId.equals(p.getId()))
            .mapToDouble(PagoOperador::getMonto).sum();
    }

    // Cuánto de lo causado en los cortes queda cubierto si en total se le han pagado "pagado" pesos
    public double cubierto(List<Gasto> causados, double pagado) {
        double total = 0;
        for (Gasto g : causados) {
            double previo = g.getAcumuladoPrevio() == null ? 0 : g.getAcumuladoPrevio();
            double ganado = g.getGanadoCorte() == null ? 0 : g.getGanadoCorte();
            total += Math.min(ganado, Math.max(0, pagado - previo));
        }
        return total;
    }

    // Parte de un pago de "monto" que cae en cortes causados (no crea gasto) dado lo ya pagado antes
    public double aplicable(Long userId, String operador, double pagadoAntes, double monto) {
        List<Gasto> causados = causados(userId, operador);
        if (causados.isEmpty()) return 0;
        double antes = cubierto(causados, pagadoAntes);
        double despues = cubierto(causados, pagadoAntes + monto);
        return Math.max(0, Math.min(monto, despues - antes));
    }

    // Crea el gasto de cada operador que trabajó la máquina en el tramo del corte
    public void causarCorte(Long userId, Corte corte, Faena faena, int numero) {
        List<HoraTrabajada> delTramo = horaRepository.findByUsuarioIdAndMaquinaNombre(userId, faena.getMaquinaNombre()).stream()
            .filter(h -> h.getFecha() != null && !h.getFecha().isBefore(corte.getFechaInicio()) && !h.getFecha().isAfter(corte.getFechaFin()))
            .filter(h -> h.getOperadorNombre() != null && !h.getOperadorNombre().isBlank())
            .toList();

        Map<String, List<HoraTrabajada>> porOperador = new LinkedHashMap<>();
        for (HoraTrabajada h : delTramo) porOperador.computeIfAbsent(h.getOperadorNombre(), k -> new java.util.ArrayList<>()).add(h);

        for (Map.Entry<String, List<HoraTrabajada>> e : porOperador.entrySet()) {
            String operador = e.getKey();
            double ganadoTramo = ganado(e.getValue());
            if (ganadoTramo <= TOLERANCIA) continue;

            Long operadorId = e.getValue().get(0).getOperadorId();
            double previo = ganado(horaRepository.findByUsuarioIdAndOperadorIdOrNombre(userId, operadorId, operador).stream()
                .filter(h -> h.getFecha() != null && h.getFecha().isBefore(corte.getFechaInicio())).toList());
            double pagado = pagadoTotal(userId, operador, null);
            // lo que ya se le pagó por encima de todo lo anterior son anticipos de este corte, que ya fueron gasto
            double credito = Math.min(ganadoTramo, Math.max(0, pagado - previo));
            double monto = ganadoTramo - credito;
            if (monto <= TOLERANCIA) continue;

            double horas = e.getValue().stream().mapToDouble(HoraTrabajada::getHoras).sum();
            Gasto g = new Gasto();
            g.setUsuarioId(userId);
            g.setCategoria(PagoOperadorService.CATEGORIA);
            g.setDescripcion("Pago operador — " + operador + ": Corte " + numero + " · " + corte.getFechaInicio().format(FECHA) + " a " + corte.getFechaFin().format(FECHA)
                + " · " + (Math.round(horas * 10) / 10.0) + " h");
            g.setMonto(Math.round(monto));
            g.setFecha(corte.getFechaFin());
            g.setMaquinaNombre(faena.getMaquinaNombre());
            g.setFaenaId(faena.getId());
            g.setCorteId(corte.getId());
            g.setOperadorNombre(operador);
            g.setGanadoCorte(ganadoTramo);
            g.setAcumuladoPrevio(previo);
            gastoRepository.save(g);
            faenaService.recalcularTotalesSiCerrada(faena.getId());
        }
    }

    // Antes de deshacer un corte: si ya se le pagó al operador por él, hay que arreglar esos pagos primero
    public void borrarCausacion(Long userId, Corte corte) {
        List<Gasto> delCorte = gastoRepository.findByUsuarioIdAndCorteIdIsNotNull(userId).stream()
            .filter(g -> corte.getId().equals(g.getCorteId())).toList();
        for (Gasto g : delCorte) {
            double ganado = g.getGanadoCorte() == null ? 0 : g.getGanadoCorte();
            double credito = ganado - g.getMonto();
            double previo = g.getAcumuladoPrevio() == null ? 0 : g.getAcumuladoPrevio();
            double pagado = pagadoTotal(userId, g.getOperadorNombre(), null);
            double aplicado = Math.min(ganado, Math.max(0, pagado - previo)) - credito;
            if (aplicado > TOLERANCIA)
                throw new IllegalStateException("A " + g.getOperadorNombre() + " ya se le pagó por este corte. Edita o borra esos pagos y vuelve a intentar.");
        }
        for (Gasto g : delCorte) {
            gastoRepository.delete(g);
            faenaService.recalcularTotalesSiCerrada(g.getFaenaId());
        }
    }

}
