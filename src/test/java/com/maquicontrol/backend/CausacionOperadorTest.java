package com.maquicontrol.backend;

import com.maquicontrol.backend.model.*;
import com.maquicontrol.backend.repository.*;
import com.maquicontrol.backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// El costo del operador se causa al hacer el corte y los pagos no lo cuentan dos veces.
// Siempre debe cumplirse: gastos de "Pago operador" = lo que ganó (cuando ya se le pagó todo lo del corte).
class CausacionOperadorTest {

    static final long U = 7L;
    static final String LUIS = "Luis Pérez";

    GastoRepository gastoRepo = mock(GastoRepository.class);
    HoraTrabajadaRepository horaRepo = mock(HoraTrabajadaRepository.class);
    PagoOperadorRepository pagoRepo = mock(PagoOperadorRepository.class);
    MaquinaRepository maquinaRepo = mock(MaquinaRepository.class);
    FaenaRepository faenaRepo = mock(FaenaRepository.class);
    FaenaService faenaService = mock(FaenaService.class);

    final Map<Long, Gasto> gastos = new LinkedHashMap<>();
    final Map<Long, PagoOperador> pagos = new LinkedHashMap<>();
    final List<HoraTrabajada> horas = new ArrayList<>();
    long seq = 0;

    CausacionOperadorService causacion = new CausacionOperadorService();
    PagoOperadorService pagoService = new PagoOperadorService();
    Faena faena;

    @BeforeEach
    void preparar() {
        ReflectionTestUtils.setField(causacion, "gastoRepository", gastoRepo);
        ReflectionTestUtils.setField(causacion, "horaRepository", horaRepo);
        ReflectionTestUtils.setField(causacion, "pagoRepository", pagoRepo);
        ReflectionTestUtils.setField(causacion, "faenaService", faenaService);
        ReflectionTestUtils.setField(pagoService, "pagoRepository", pagoRepo);
        ReflectionTestUtils.setField(pagoService, "gastoRepository", gastoRepo);
        ReflectionTestUtils.setField(pagoService, "maquinaRepository", maquinaRepo);
        ReflectionTestUtils.setField(pagoService, "faenaRepository", faenaRepo);
        ReflectionTestUtils.setField(pagoService, "faenaService", faenaService);
        ReflectionTestUtils.setField(pagoService, "causacion", causacion);

        when(gastoRepo.save(any())).thenAnswer(i -> { Gasto g = i.getArgument(0); if (g.getId() == null) g.setId(++seq); gastos.put(g.getId(), g); return g; });
        when(gastoRepo.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(gastos.get(i.getArgument(0))));
        doAnswer(i -> { Gasto g = i.getArgument(0); gastos.remove(g.getId()); return null; }).when(gastoRepo).delete(any());
        when(gastoRepo.findByUsuarioIdAndCorteIdIsNotNull(U)).thenAnswer(i -> gastos.values().stream().filter(g -> g.getCorteId() != null).toList());
        when(pagoRepo.save(any())).thenAnswer(i -> { PagoOperador p = i.getArgument(0); if (p.getId() == null) p.setId(++seq); pagos.put(p.getId(), p); return p; });
        when(pagoRepo.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(pagos.get(i.getArgument(0))));
        when(pagoRepo.findByUsuarioIdAndOperadorNombre(eq(U), anyString())).thenAnswer(i ->
            pagos.values().stream().filter(p -> p.getOperadorNombre().equals(i.getArgument(1))).toList());
        when(horaRepo.findByUsuarioIdAndMaquinaNombre(eq(U), anyString())).thenAnswer(i ->
            horas.stream().filter(h -> h.getMaquinaNombre().equals(i.getArgument(1))).toList());
        when(horaRepo.findByUsuarioIdAndOperadorIdOrNombre(eq(U), any(), anyString())).thenAnswer(i ->
            horas.stream().filter(h -> h.getOperadorNombre().equals(i.getArgument(2))).toList());
        when(maquinaRepo.findByUsuarioIdAndOperadorNombre(anyLong(), anyString())).thenReturn(List.of());
        when(faenaRepo.findByUsuarioIdAndMaquinaNombre(anyLong(), anyString())).thenReturn(List.of());

        faena = new Faena();
        faena.setId(5L); faena.setUsuarioId(U); faena.setMaquinaNombre("Excavadora");
    }

    void horas(String desde, int dias, double hPorDia, double valorHora) {
        LocalDate d = LocalDate.parse(desde);
        for (int i = 0; i < dias; i++) {
            HoraTrabajada h = new HoraTrabajada();
            h.setOperadorId(3L); h.setOperadorNombre(LUIS); h.setMaquinaNombre("Excavadora");
            h.setHoras(hPorDia); h.setValorHora(valorHora); h.setFecha(d.plusDays(i));
            horas.add(h);
        }
    }

    Corte corte(String ini, String fin) {
        Corte c = new Corte();
        c.setId(++seq); c.setUsuarioId(U); c.setFaenaId(5L); c.setFechaInicio(LocalDate.parse(ini)); c.setFechaFin(LocalDate.parse(fin));
        return c;
    }

    PagoOperador paga(double monto) {
        PagoOperador p = new PagoOperador();
        p.setOperadorNombre(LUIS); p.setMonto(monto); p.setFecha(LocalDate.parse("2026-11-01"));
        return pagoService.guardar(U, p);
    }

    double totalGastos() { return gastos.values().stream().mapToDouble(Gasto::getMonto).sum(); }

    @Test
    void sinCortesCausados_elPagoCreaSuGastoComoSiempre() {
        PagoOperador p = paga(1_000_000);
        assertEquals(0, p.getAplicado(), 0.001);
        assertNotNull(p.getGastoGeneradoId());
        assertEquals(1_000_000, totalGastos(), 0.001);
    }

    @Test
    void alCortar_naceElGastoDelOperadorPorLoQueGano() {
        horas("2026-10-01", 10, 10, 30_000);           // 100 h × 30.000 = 3 M
        causacion.causarCorte(U, corte("2026-10-01", "2026-10-10"), faena, 1);

        assertEquals(1, gastos.size());
        Gasto g = gastos.values().iterator().next();
        assertEquals(3_000_000, g.getMonto(), 0.001);
        assertEquals(PagoOperadorService.CATEGORIA, g.getCategoria());
        assertEquals(LUIS, g.getOperadorNombre());
        assertEquals(LocalDate.parse("2026-10-10"), g.getFecha());
        assertEquals(5L, g.getFaenaId());
    }

    @Test
    void pagarElCorte_noDuplicaElGasto() {
        horas("2026-10-01", 10, 10, 30_000);
        causacion.causarCorte(U, corte("2026-10-01", "2026-10-10"), faena, 1);

        PagoOperador p1 = paga(1_000_000);
        assertEquals(1_000_000, p1.getAplicado(), 0.001);
        assertNull(p1.getGastoGeneradoId(), "lo que cubre un corte no crea gasto");
        assertEquals(3_000_000, totalGastos(), 0.001);

        PagoOperador p2 = paga(2_000_000);
        assertNull(p2.getGastoGeneradoId());
        assertEquals(3_000_000, totalGastos(), 0.001, "pagado todo el corte, el gasto sigue siendo lo ganado");
    }

    @Test
    void loQueSobraDelCorte_esAnticipoYSiCreaGasto() {
        horas("2026-10-01", 10, 10, 30_000);
        causacion.causarCorte(U, corte("2026-10-01", "2026-10-10"), faena, 1);

        PagoOperador p = paga(3_500_000);       // el corte debe 3 M, 0,5 M van adelantados
        assertEquals(3_000_000, p.getAplicado(), 0.001);
        assertNotNull(p.getGastoGeneradoId());
        assertEquals(500_000, gastos.get(p.getGastoGeneradoId()).getMonto(), 0.001);
        assertEquals(3_500_000, totalGastos(), 0.001);
    }

    @Test
    void unAnticipoQueYaFueGasto_seDescuentaDelGastoDelCorte() {
        horas("2026-10-01", 10, 10, 30_000);
        paga(1_000_000);                         // anticipo antes del corte: gasto propio
        assertEquals(1_000_000, totalGastos(), 0.001);

        causacion.causarCorte(U, corte("2026-10-01", "2026-10-10"), faena, 1);
        Gasto causado = gastos.values().stream().filter(g -> g.getCorteId() != null).findFirst().orElseThrow();
        assertEquals(2_000_000, causado.getMonto(), 0.001, "se causa solo lo que no era gasto todavía");
        assertEquals(3_000_000, causado.getGanadoCorte(), 0.001);
        assertEquals(3_000_000, totalGastos(), 0.001, "anticipo + gasto del corte = lo ganado");

        paga(2_000_000);                          // el resto del corte
        assertEquals(3_000_000, totalGastos(), 0.001);
    }

    @Test
    void unaDeudaVieja_sePagaPrimeroYSigueSiendoGastoCuandoSePaga() {
        horas("2026-09-01", 10, 10, 30_000);      // 3 M de septiembre, sin pagar y sin causar
        horas("2026-10-01", 10, 10, 30_000);
        causacion.causarCorte(U, corte("2026-10-01", "2026-10-10"), faena, 2);

        PagoOperador p = paga(3_000_000);         // salda lo de septiembre, no el corte nuevo
        assertEquals(0, p.getAplicado(), 0.001);
        assertEquals(3_000_000, gastos.get(p.getGastoGeneradoId()).getMonto(), 0.001);
        assertEquals(6_000_000, totalGastos(), 0.001, "septiembre al pagarse + octubre causado");

        PagoOperador q = paga(3_000_000);         // ahora sí el corte de octubre
        assertEquals(3_000_000, q.getAplicado(), 0.001);
        assertNull(q.getGastoGeneradoId());
        assertEquals(6_000_000, totalGastos(), 0.001);
    }

    @Test
    void siYaSePagoTodo_noSeCausaGastoDeMas() {
        horas("2026-10-01", 10, 10, 30_000);
        paga(3_000_000);                          // pagó todo por adelantado (ya es gasto)
        causacion.causarCorte(U, corte("2026-10-01", "2026-10-10"), faena, 1);
        assertEquals(1, gastos.size(), "no hay nada nuevo que causar");
        assertEquals(3_000_000, totalGastos(), 0.001);
    }

    @Test
    void deshacerElCorte_borraSuGastoSiNoTienePagos() {
        horas("2026-10-01", 10, 10, 30_000);
        Corte c = corte("2026-10-01", "2026-10-10");
        causacion.causarCorte(U, c, faena, 1);
        causacion.borrarCausacion(U, c);
        assertTrue(gastos.isEmpty());
    }

    @Test
    void deshacerElCorte_conPagosAplicados_seBloquea() {
        horas("2026-10-01", 10, 10, 30_000);
        Corte c = corte("2026-10-01", "2026-10-10");
        causacion.causarCorte(U, c, faena, 1);
        paga(1_000_000);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> causacion.borrarCausacion(U, c));
        assertTrue(e.getMessage().contains("ya se le pagó"));
        assertEquals(1, gastos.size());
    }

    @Test
    void borrarUnPago_devuelveElSaldoDelCorte() {
        horas("2026-10-01", 10, 10, 30_000);
        causacion.causarCorte(U, corte("2026-10-01", "2026-10-10"), faena, 1);
        PagoOperador p = paga(1_000_000);
        pagos.remove(p.getId());                  // se borra el pago
        PagoOperador otro = paga(3_000_000);      // el corte debe otra vez los 3 M completos
        assertEquals(3_000_000, otro.getAplicado(), 0.001);
        assertEquals(3_000_000, totalGastos(), 0.001);
    }

    @Test
    void editarUnPago_vuelveARepartirlo() {
        horas("2026-10-01", 10, 10, 30_000);
        causacion.causarCorte(U, corte("2026-10-01", "2026-10-10"), faena, 1);
        PagoOperador p = paga(1_000_000);
        assertEquals(1_000_000, p.getAplicado(), 0.001);

        PagoOperador cambio = new PagoOperador();
        cambio.setOperadorNombre(LUIS); cambio.setMonto(3_800_000); cambio.setFecha(p.getFecha()); cambio.setDescripcion("ajuste");
        PagoOperador editado = pagoService.actualizar(p.getId(), cambio);

        assertEquals(3_000_000, editado.getAplicado(), 0.001);
        assertNotNull(editado.getGastoGeneradoId(), "los 800.000 de más son anticipo y sí son gasto");
        assertEquals(800_000, gastos.get(editado.getGastoGeneradoId()).getMonto(), 0.001);
        assertEquals(3_800_000, totalGastos(), 0.001);

        cambio.setMonto(500_000);
        PagoOperador menor = pagoService.actualizar(p.getId(), cambio);
        assertEquals(500_000, menor.getAplicado(), 0.001);
        assertNull(menor.getGastoGeneradoId(), "ya cae todo en el corte: se quita el gasto propio");
        assertEquals(3_000_000, totalGastos(), 0.001);
    }
}
