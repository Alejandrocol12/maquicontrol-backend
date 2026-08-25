package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Faena;
import com.maquicontrol.backend.model.HoraTrabajada;
import com.maquicontrol.backend.model.Periodo;
import com.maquicontrol.backend.model.Salario;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.GastoRepository;
import com.maquicontrol.backend.repository.HoraTrabajadaRepository;
import com.maquicontrol.backend.repository.IngresoRepository;
import com.maquicontrol.backend.repository.MaquinaRepository;
import com.maquicontrol.backend.repository.MantenimientoRepository;
import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.repository.PeriodoRepository;
import com.maquicontrol.backend.repository.SalarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class FaenaService {

    @Autowired private FaenaRepository faenaRepository;
    @Autowired private IngresoRepository ingresoRepository;
    @Autowired private GastoRepository gastoRepository;
    @Autowired private MantenimientoRepository mantenimientoRepository;
    @Autowired private HoraTrabajadaRepository horaTrabajadaRepository;
    @Autowired private MaquinaRepository maquinaRepository;
    @Autowired private SalarioRepository salarioRepository;
    @Autowired private PeriodoRepository periodoRepository;
    @Autowired private OperadorRepository operadorRepository;

    public List<Faena> obtenerTodas(Long userId) {
        return faenaRepository.findByUsuarioId(userId);
    }

    public Optional<Faena> obtenerPorId(Long id) {
        return faenaRepository.findById(id);
    }

    public Optional<Faena> obtenerActiva(Long userId, String maquinaNombre) {
        return faenaRepository.findByUsuarioIdAndMaquinaNombreAndEstado(userId, maquinaNombre, "activa");
    }

    public Faena crear(Long userId, Faena faena) {
        faena.setUsuarioId(userId);
        faena.setEstado("activa");
        if (faena.getFechaInicio() == null) faena.setFechaInicio(LocalDate.now());
        return faenaRepository.save(faena);
    }

    public Faena actualizar(Long id, Faena datos) {
        Faena faena = faenaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Faena no encontrada"));
        faena.setNombreObra(datos.getNombreObra());
        faena.setCliente(datos.getCliente());
        faena.setNota(datos.getNota());
        return faenaRepository.save(faena);
    }

    @Transactional
    public Faena cerrar(Long id) {
        Faena faena = faenaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Faena no encontrada"));

        double totalIngresos = ingresoRepository.findByFaenaId(id)
            .stream().mapToDouble(i -> i.getTotal()).sum();

        double totalGastos = gastoRepository.findByFaenaId(id)
            .stream().mapToDouble(g -> g.getMonto()).sum();

        double totalMantenimientos = mantenimientoRepository.findByFaenaId(id)
            .stream().mapToDouble(m -> m.getCosto()).sum();

        // Crear registro Salario del operador (reemplaza cualquier resumen previo, ver crearResumenSalarioOperador)
        crearResumenSalarioOperador(faena, id);

        // Cerrar el periodo de pago del operador y abrir uno nuevo en cero
        resetearPeriodoOperador(faena);

        faena.setEstado("cerrada");
        faena.setFechaFin(LocalDate.now());
        faena.setTotalIngresos(totalIngresos);
        faena.setTotalGastos(totalGastos);
        faena.setTotalMantenimientos(totalMantenimientos);
        // totalGastos ya incluye los costos de mantenimiento (MantenimientoService crea un Gasto espejo
        // por cada mantenimiento con costo); totalMantenimientos es solo informativo, no se resta aparte.
        faena.setUtilidadNeta(totalIngresos - totalGastos);
        return faenaRepository.save(faena);
    }

    @Transactional
    public Faena reabrir(Long id) {
        Faena faena = faenaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Faena no encontrada"));
        faena.setEstado("activa");
        faena.setFechaFin(null);
        return faenaRepository.save(faena);
    }

    private void crearResumenSalarioOperador(Faena faena, Long faenaId) {
        // Evita duplicar el resumen si el periodo se reabrió y se vuelve a cerrar
        salarioRepository.deleteByFaenaId(faenaId);

        var horasFaena = horaTrabajadaRepository.findByFaenaId(faenaId);
        if (horasFaena.isEmpty()) return;

        double totalHoras = horasFaena.stream().mapToDouble(h -> h.getHoras()).sum();
        if (totalHoras <= 0) return;

        maquinaRepository.findByUsuarioIdAndNombre(faena.getUsuarioId(), faena.getMaquinaNombre())
            .ifPresent(maq -> {
                String operadorNombre = maq.getOperadorNombre();
                double valorHoraOp = maq.getValorHoraOperador();
                if (operadorNombre == null || operadorNombre.isBlank() || valorHoraOp <= 0) return;

                double totalBruto = totalHoras * valorHoraOp;

                // Solo registro Salario — el costo se suma en Finanzas desde la tabla salarios
                Salario sal = new Salario();
                sal.setUsuarioId(faena.getUsuarioId());
                sal.setOperadorNombre(operadorNombre);
                sal.setMaquinaNombre(faena.getMaquinaNombre());
                sal.setFaenaId(faenaId);
                sal.setHorasTrabajadas(totalHoras);
                sal.setValorHora(valorHoraOp);
                sal.setTotalBruto(totalBruto);
                sal.setAnticipos(0);
                sal.setTotalNeto(totalBruto);
                sal.setFecha(LocalDate.now());
                sal.setEstado("Pagado");
                salarioRepository.save(sal);
            });
    }

    private void resetearPeriodoOperador(Faena faena) {
        if (faena.getMaquinaNombre() == null) return;

        maquinaRepository.findByUsuarioIdAndNombre(faena.getUsuarioId(), faena.getMaquinaNombre())
            .ifPresent(maq -> {
                String operadorNombre = maq.getOperadorNombre();
                if (operadorNombre == null || operadorNombre.isBlank()) return;

                operadorRepository.findByUsuarioId(faena.getUsuarioId()).stream()
                    .filter(o -> operadorNombre.equals(o.getNombre()))
                    .findFirst()
                    .ifPresent(operador -> {
                        List<HoraTrabajada> todasHoras = horaTrabajadaRepository.findByOperadorNombre(operadorNombre);

                        periodoRepository.findByOperadorIdAndEstado(operador.getId(), "activo")
                            .ifPresent(periodoActivo -> {
                                // Calcular horas del periodo activo
                                double horasPeriodo;
                                if (periodoActivo.getDesdeHoraId() != null) {
                                    final Long ancla = periodoActivo.getDesdeHoraId();
                                    horasPeriodo = todasHoras.stream()
                                        .filter(h -> h.getId() > ancla)
                                        .mapToDouble(HoraTrabajada::getHoras)
                                        .sum();
                                } else {
                                    String desde = periodoActivo.getFechaInicio();
                                    horasPeriodo = todasHoras.stream()
                                        .filter(h -> desde == null || h.getFecha() == null
                                            || h.getFecha().toString().compareTo(desde) >= 0)
                                        .mapToDouble(HoraTrabajada::getHoras)
                                        .sum();
                                }

                                double bruto = horasPeriodo * maq.getValorHoraOperador();
                                double neto  = bruto - periodoActivo.getAnticipos();

                                // Cerrar el periodo actual con resumen
                                periodoActivo.setEstado("cerrado");
                                periodoActivo.setFechaFin(LocalDate.now().toString());
                                periodoActivo.setHorasTotal(horasPeriodo);
                                periodoActivo.setSalarioBruto(bruto);
                                periodoActivo.setSalarioNeto(neto);
                                periodoRepository.save(periodoActivo);
                            });

                        // Crear nuevo periodo en cero anclado al ultimo ID de hora
                        long maxHoraId = todasHoras.stream()
                            .mapToLong(HoraTrabajada::getId)
                            .max()
                            .orElse(0L);

                        Periodo nuevo = new Periodo();
                        nuevo.setUsuarioId(faena.getUsuarioId());
                        nuevo.setOperadorId(operador.getId());
                        nuevo.setEstado("activo");
                        nuevo.setFechaInicio(LocalDate.now().toString());
                        nuevo.setDesdeHoraId(maxHoraId > 0 ? maxHoraId : null);
                        nuevo.setAnticipos(0);
                        periodoRepository.save(nuevo);
                    });
            });
    }

    public void eliminar(Long id) {
        faenaRepository.deleteById(id);
    }
}
