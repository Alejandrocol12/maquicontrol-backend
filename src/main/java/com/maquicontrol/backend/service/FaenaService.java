package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.Faena;
import com.maquicontrol.backend.model.HoraTrabajada;
import com.maquicontrol.backend.model.Periodo;
import com.maquicontrol.backend.model.Salario;
import com.maquicontrol.backend.repository.CombustibleRepository;
import com.maquicontrol.backend.repository.CorteRepository;
import com.maquicontrol.backend.repository.FaenaRepository;
import com.maquicontrol.backend.repository.PagoClienteRepository;
import com.maquicontrol.backend.repository.PagoOperadorRepository;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    @Autowired private CombustibleRepository combustibleRepository;
    @Autowired private CorteRepository corteRepository;
    @Autowired private PagoClienteRepository pagoClienteRepository;
    @Autowired private PagoOperadorRepository pagoOperadorRepository;

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
        if ("gastos".equals(faena.getTipo())) return crearDeGastos(userId, faena);
        faena.setTipo("trabajo");
        if (faena.getMaquinaNombre() != null
                && faenaRepository.findByUsuarioIdAndMaquinaNombreAndEstado(userId, faena.getMaquinaNombre(), "activa").isPresent()) {
            throw new IllegalStateException(
                "Ya hay un periodo activo en la máquina " + faena.getMaquinaNombre() + " — ciérralo antes de abrir uno nuevo.");
        }
        faena.setUsuarioId(userId);
        faena.setEstado("activa");
        if (faena.getFechaInicio() == null) faena.setFechaInicio(LocalDate.now());
        return faenaRepository.save(faena);
    }

    // Periodo solo para gastos externos: no cuenta como periodo de trabajo ni toca la nómina del
    // operador. Su estado es "abierta" (no "activa"), así que el resto de la app no lo confunde con
    // el periodo en campo de la máquina. Solo puede haber uno abierto por máquina.
    private Faena crearDeGastos(Long userId, Faena faena) {
        if (faena.getMaquinaNombre() == null || faena.getMaquinaNombre().isBlank()) {
            throw new IllegalStateException("Escoge la máquina del periodo de gastos.");
        }
        boolean yaHay = faenaRepository.findByUsuarioIdAndMaquinaNombre(userId, faena.getMaquinaNombre()).stream()
            .anyMatch(f -> "gastos".equals(f.getTipo()) && "abierta".equals(f.getEstado()));
        if (yaHay) {
            throw new IllegalStateException("Ya hay un periodo de gastos externos abierto en " + faena.getMaquinaNombre() + " — ciérralo antes de abrir otro.");
        }
        faena.setUsuarioId(userId);
        faena.setTipo("gastos");
        faena.setEstado("abierta");
        faena.setCliente(null);
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

    // Fechas y meta de horas del corte según el cliente (los tres son opcionales: vacío = se quita)
    @Transactional
    public Faena actualizarCiclo(Long id, LocalDate desde, LocalDate hasta, Double meta) {
        Faena faena = faenaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Faena no encontrada"));
        if (desde != null && hasta != null && hasta.isBefore(desde))
            throw new IllegalArgumentException("La fecha del corte no puede ser antes de la fecha de inicio");
        if (meta != null && meta < 0) throw new IllegalArgumentException("La meta de horas no puede ser negativa");
        faena.setCorteDesde(desde);
        faena.setCorteHasta(hasta);
        faena.setMetaHorasCorte(meta != null && meta > 0 ? meta : null);
        return faenaRepository.save(faena);
    }

    @Transactional
    public Faena cerrar(Long id) {
        Faena faena = faenaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Faena no encontrada"));

        // Periodo de gastos externos: solo se cierra, sin salario ni corte de nómina
        if ("gastos".equals(faena.getTipo())) {
            faena.setEstado("cerrada");
            faena.setFechaFin(LocalDate.now());
            faena.setTotalGastos(gastoRepository.findByFaenaId(id).stream().mapToDouble(g -> g.getMonto()).sum());
            return faenaRepository.save(faena);
        }

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
        if ("gastos".equals(faena.getTipo())) {
            boolean otra = faenaRepository.findByUsuarioIdAndMaquinaNombre(faena.getUsuarioId(), faena.getMaquinaNombre()).stream()
                .anyMatch(f -> !f.getId().equals(faena.getId()) && "gastos".equals(f.getTipo()) && "abierta".equals(f.getEstado()));
            if (otra) throw new IllegalStateException("Ya hay otro periodo de gastos externos abierto en " + faena.getMaquinaNombre() + " — ciérralo antes de reabrir este.");
            faena.setEstado("abierta");
            faena.setFechaFin(null);
            return faenaRepository.save(faena);
        }
        if (hayOtraFaenaActivaEnMaquina(faena)) {
            throw new IllegalStateException(
                "Ya hay otro periodo activo en la máquina " + faena.getMaquinaNombre()
                + " — ciérralo antes de reabrir este, o el operador quedaría con dos periodos de pago activos a la vez.");
        }
        faena.setEstado("activa");
        faena.setFechaFin(null);
        deshacerCortePeriodoOperador(id);
        return faenaRepository.save(faena);
    }

    // Deshace solo el corte de nomina que origino el ultimo cierre de esta faena, sin tocar
    // el estado de la faena en si. Sirve para corregir un corte accidental (por ejemplo si
    // se cerro una faena mientras el operador seguia con otra activa en la misma maquina,
    // antes de que existiera esa validacion) sin necesidad de reabrir la faena.
    @Transactional
    public void deshacerCorteOperadorPorFaena(Long id) {
        faenaRepository.findById(id).orElseThrow(() -> new RuntimeException("Faena no encontrada"));
        deshacerCortePeriodoOperador(id);
    }

    // Verdadero si existe OTRA faena (distinta de esta) en estado "activa" para la misma
    // maquina. Se usa para no permitir dos periodos activos simultaneos en una maquina
    // (rompe el supuesto de "un periodo activo por maquina" que usa el resto de la app,
    // incluido el corte de nomina del operador) y para no cortarle la nomina al operador
    // si sigue trabajando en otra faena de la misma maquina.
    private boolean hayOtraFaenaActivaEnMaquina(Faena faena) {
        if (faena.getMaquinaNombre() == null) return false;
        return faenaRepository.findByUsuarioIdAndMaquinaNombreAndEstado(
                faena.getUsuarioId(), faena.getMaquinaNombre(), "activa")
            .filter(f -> !f.getId().equals(faena.getId()))
            .isPresent();
    }

    // Recalcula solo los totales guardados de una faena YA CERRADA, sin tocar estado,
    // fechaFin, salario ni el periodo del operador. Se llama cada vez que se crea/edita/
    // borra un ingreso, gasto o mantenimiento para que el resumen no quede desactualizado
    // (antes solo se recalculaba al reabrir+cerrar, y cualquier otro camino dejaba el
    // snapshot desincronizado de los registros reales).
    public void recalcularTotalesSiCerrada(Long faenaId) {
        if (faenaId == null) return;
        faenaRepository.findById(faenaId).ifPresent(faena -> {
            if (!"cerrada".equals(faena.getEstado())) return;
            double totalIngresos = ingresoRepository.findByFaenaId(faenaId)
                .stream().mapToDouble(i -> i.getTotal()).sum();
            double totalGastos = gastoRepository.findByFaenaId(faenaId)
                .stream().mapToDouble(g -> g.getMonto()).sum();
            double totalMantenimientos = mantenimientoRepository.findByFaenaId(faenaId)
                .stream().mapToDouble(m -> m.getCosto()).sum();
            faena.setTotalIngresos(totalIngresos);
            faena.setTotalGastos(totalGastos);
            faena.setTotalMantenimientos(totalMantenimientos);
            faena.setUtilidadNeta(totalIngresos - totalGastos);
            faenaRepository.save(faena);
        });
    }

    // Si el cierre de esta faena origino un corte de nomina del operador (un Periodo nuevo
    // en cero) y ese periodo sigue intacto -- sin anticipos pagados contra el -- lo deshace:
    // reabre el periodo anterior y borra el que se habia creado, para que al operador le
    // quede una sola linea continua en vez de fragmentarse cada vez que se reabre y se
    // vuelve a cerrar la misma faena.
    private void deshacerCortePeriodoOperador(Long faenaId) {
        periodoRepository.findByFaenaIdAndEstado(faenaId, "activo").ifPresent(periodoNuevo -> {
            if (periodoNuevo.getAnticipos() > 0) return;

            periodoRepository.findFirstByOperadorIdAndEstadoOrderByIdDesc(periodoNuevo.getOperadorId(), "cerrado")
                .ifPresent(periodoAnterior -> {
                    periodoAnterior.setEstado("activo");
                    periodoAnterior.setFechaFin(null);
                    periodoAnterior.setHorasTotal(0);
                    periodoAnterior.setSalarioBruto(0);
                    periodoAnterior.setSalarioNeto(0);
                    periodoRepository.save(periodoAnterior);
                    periodoRepository.delete(periodoNuevo);
                });
        });
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
        // Si la maquina sigue teniendo otra faena activa, el operador sigue trabajando:
        // no se le corta el periodo de pago solo porque esta faena en particular se cerro.
        if (hayOtraFaenaActivaEnMaquina(faena)) return;

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
                        nuevo.setFaenaId(faena.getId());
                        periodoRepository.save(nuevo);
                    });
            });
    }

    // Eliminar un periodo borra también todo lo que se registró dentro de él (ingresos, gastos,
    // combustible, mantenimientos, horas, nómina y cortes), para que no queden registros sueltos
    // en Finanzas. Devuelve cuántos registros de cada tipo se borraron.
    @Transactional
    public Map<String, Integer> eliminar(Long userId, Long id) {
        Map<String, Integer> borrados = borrarRegistrosDePeriodo(userId, id);
        faenaRepository.deleteById(id);
        return borrados;
    }

    // Periodos que ya no existen pero cuyos registros siguen guardados (se eliminaron antes de que
    // el borrado fuera en cascada). Se ven en Finanzas como "Sin periodo asignado".
    private Set<Long> idsHuerfanos(Long userId) {
        Set<Long> vivos = new HashSet<>();
        faenaRepository.findByUsuarioId(userId).forEach(f -> vivos.add(f.getId()));
        Set<Long> huerfanos = new HashSet<>();
        ingresoRepository.findByUsuarioId(userId).forEach(x -> { if (x.getFaenaId() != null && !vivos.contains(x.getFaenaId())) huerfanos.add(x.getFaenaId()); });
        gastoRepository.findByUsuarioId(userId).forEach(x -> { if (x.getFaenaId() != null && !vivos.contains(x.getFaenaId())) huerfanos.add(x.getFaenaId()); });
        combustibleRepository.findByUsuarioId(userId).forEach(x -> { if (x.getFaenaId() != null && !vivos.contains(x.getFaenaId())) huerfanos.add(x.getFaenaId()); });
        mantenimientoRepository.findByUsuarioId(userId).forEach(x -> { if (x.getFaenaId() != null && !vivos.contains(x.getFaenaId())) huerfanos.add(x.getFaenaId()); });
        horaTrabajadaRepository.findByUsuarioId(userId).forEach(x -> { if (x.getFaenaId() != null && !vivos.contains(x.getFaenaId())) huerfanos.add(x.getFaenaId()); });
        corteRepository.findByUsuarioId(userId).forEach(x -> { if (x.getFaenaId() != null && !vivos.contains(x.getFaenaId())) huerfanos.add(x.getFaenaId()); });
        return huerfanos;
    }

    private static Map<String, Integer> conteoVacio() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String k : new String[]{"ingresos", "gastos", "combustibles", "mantenimientos", "horas", "cortes"}) m.put(k, 0);
        return m;
    }

    // Cuántos registros sueltos hay, sin borrar nada
    public Map<String, Integer> contarHuerfanos(Long userId) {
        Set<Long> huerfanos = idsHuerfanos(userId);
        Map<String, Integer> m = conteoVacio();
        if (huerfanos.isEmpty()) return m;
        Set<Long> conPago = gastosDePagosAlOperador(userId);
        ingresoRepository.findByUsuarioId(userId).stream().filter(x -> huerfanos.contains(x.getFaenaId())).forEach(x -> m.merge("ingresos", 1, Integer::sum));
        gastoRepository.findByUsuarioId(userId).stream().filter(x -> huerfanos.contains(x.getFaenaId()) && !conPago.contains(x.getId())).forEach(x -> m.merge("gastos", 1, Integer::sum));
        combustibleRepository.findByUsuarioId(userId).stream().filter(x -> huerfanos.contains(x.getFaenaId())).forEach(x -> m.merge("combustibles", 1, Integer::sum));
        mantenimientoRepository.findByUsuarioId(userId).stream().filter(x -> huerfanos.contains(x.getFaenaId())).forEach(x -> m.merge("mantenimientos", 1, Integer::sum));
        horaTrabajadaRepository.findByUsuarioId(userId).stream().filter(x -> huerfanos.contains(x.getFaenaId())).forEach(x -> m.merge("horas", 1, Integer::sum));
        corteRepository.findByUsuarioId(userId).stream().filter(x -> huerfanos.contains(x.getFaenaId())).forEach(x -> m.merge("cortes", 1, Integer::sum));
        return m;
    }

    @Transactional
    public Map<String, Integer> limpiarHuerfanos(Long userId) {
        Map<String, Integer> total = conteoVacio();
        for (Long faenaId : idsHuerfanos(userId)) {
            borrarRegistrosDePeriodo(userId, faenaId).forEach((k, v) -> total.merge(k, v, Integer::sum));
        }
        return total;
    }

    // Los gastos que nacieron de un pago al operador no se borran con el periodo: es plata que de
    // verdad se le pagó. Solo se desligan del periodo.
    private Set<Long> gastosDePagosAlOperador(Long userId) {
        Set<Long> ids = new HashSet<>();
        pagoOperadorRepository.findByUsuarioId(userId).forEach(p -> { if (p.getGastoGeneradoId() != null) ids.add(p.getGastoGeneradoId()); });
        return ids;
    }

    private Map<String, Integer> borrarRegistrosDePeriodo(Long userId, Long faenaId) {
        Map<String, Integer> m = conteoVacio();
        Set<Long> conPago = gastosDePagosAlOperador(userId);

        var ingresos = ingresoRepository.findByFaenaId(faenaId).stream().filter(x -> userId.equals(x.getUsuarioId())).toList();
        ingresoRepository.deleteAll(ingresos); m.put("ingresos", ingresos.size());

        var gastos = gastoRepository.findByFaenaId(faenaId).stream().filter(x -> userId.equals(x.getUsuarioId())).toList();
        int borrados = 0;
        for (var g : gastos) {
            if (conPago.contains(g.getId())) { g.setFaenaId(null); gastoRepository.save(g); }
            else { gastoRepository.delete(g); borrados++; }
        }
        m.put("gastos", borrados);

        var combustibles = combustibleRepository.findByFaenaId(faenaId).stream().filter(x -> userId.equals(x.getUsuarioId())).toList();
        combustibleRepository.deleteAll(combustibles); m.put("combustibles", combustibles.size());

        var mantenimientos = mantenimientoRepository.findByFaenaId(faenaId).stream().filter(x -> userId.equals(x.getUsuarioId())).toList();
        mantenimientoRepository.deleteAll(mantenimientos); m.put("mantenimientos", mantenimientos.size());

        var horas = horaTrabajadaRepository.findByFaenaId(faenaId).stream().filter(x -> userId.equals(x.getUsuarioId())).toList();
        horaTrabajadaRepository.deleteAll(horas); m.put("horas", horas.size());

        var salarios = salarioRepository.findByFaenaId(faenaId).stream().filter(x -> userId.equals(x.getUsuarioId())).toList();
        salarioRepository.deleteAll(salarios);

        var cortes = corteRepository.findByFaenaId(faenaId).stream().filter(x -> userId.equals(x.getUsuarioId())).toList();
        for (var c : cortes) {
            // el cobro que creó el corte se borra solo si todavía no tiene abonos
            if (c.getPagoClienteId() != null) {
                pagoClienteRepository.findById(c.getPagoClienteId()).filter(p -> p.getValorPagado() <= 0).ifPresent(pagoClienteRepository::delete);
            }
        }
        corteRepository.deleteAll(cortes); m.put("cortes", cortes.size());
        return m;
    }
}
