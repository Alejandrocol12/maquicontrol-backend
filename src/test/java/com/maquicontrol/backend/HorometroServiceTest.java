package com.maquicontrol.backend;

import com.maquicontrol.backend.model.*;
import com.maquicontrol.backend.repository.*;
import com.maquicontrol.backend.service.*;
import com.maquicontrol.backend.service.TelegramApi.Boton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Prueba el recorrido de la foto del horómetro sin Telegram ni IA: los mensajes se guardan en una lista.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HorometroServiceTest {

    @Mock LecturaHorometroRepository lecturaRepo;
    @Mock MaquinaRepository maquinaRepo;
    @Mock OperadorRepository operadorRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock HoraTrabajadaRepository horaRepo;
    @Mock HoraTrabajadaService horaService;
    @Mock IngresoService ingresoService;
    @Mock TelegramApi api;
    @Mock LectorHorometro lector;
    @InjectMocks HorometroService svc;

    static final long CHAT_OP = 111, CHAT_DUENO = 555;
    final Map<Long, LecturaHorometro> bd = new LinkedHashMap<>();
    final List<String> mensajes = new ArrayList<>();           // lo que recibe el operador
    final List<String> alDueno = new ArrayList<>();            // lo que recibe el dueño
    final List<List<List<Boton>>> botones = new ArrayList<>(); // botones de cada mensaje al operador
    Operador op;
    Maquina ex;
    Usuario dueno;
    long seq = 0;

    @BeforeEach
    void preparar() {
        ReflectionTestUtils.setField(svc, "maxHoras", 16.0);
        ReflectionTestUtils.setField(svc, "confianzaMinima", 85);
        ReflectionTestUtils.setField(svc, "retencionDias", 60);

        op = new Operador(); op.setId(3L); op.setUsuarioId(7L); op.setNombre("Luis Pérez");
        ex = maquina(1L, "Excavadora CAT 320", 8412.5);
        dueno = new Usuario(); dueno.setId(7L); dueno.setNombre("Alejandro"); dueno.setTelegramChatId(CHAT_DUENO);

        when(lecturaRepo.save(any())).thenAnswer(i -> {
            LecturaHorometro l = i.getArgument(0);
            if (l.getId() == null) l.setId(++seq);
            bd.put(l.getId(), l);
            return l;
        });
        when(lecturaRepo.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(bd.get(i.getArgument(0))));
        when(lecturaRepo.findByOperadorIdAndEstadoIn(anyLong(), any())).thenAnswer(i ->
                bd.values().stream().filter(l -> l.getOperadorId().equals(i.getArgument(0)) && ((Collection<?>) i.getArgument(1)).contains(l.getEstado())).toList());
        when(lecturaRepo.findFirstByOperadorIdAndEstadoInOrderByIdDesc(anyLong(), any())).thenAnswer(i ->
                bd.values().stream().filter(l -> l.getOperadorId().equals(i.getArgument(0)) && ((Collection<?>) i.getArgument(1)).contains(l.getEstado()))
                        .reduce((a, b) -> b));
        when(lecturaRepo.findByUsuarioIdAndEstadoOrderByIdDesc(anyLong(), anyString())).thenAnswer(i ->
                bd.values().stream().filter(l -> l.getEstado().equals(i.getArgument(1))).toList());
        when(operadorRepo.findById(3L)).thenReturn(Optional.of(op));
        when(usuarioRepo.findById(7L)).thenReturn(Optional.of(dueno));
        when(usuarioRepo.findByTelegramChatId(CHAT_DUENO)).thenReturn(Optional.of(dueno));
        when(maquinaRepo.findById(1L)).thenAnswer(i -> Optional.of(ex));
        when(maquinaRepo.findByUsuarioIdAndOperadorNombre(7L, "Luis Pérez")).thenAnswer(i -> List.of(ex));
        when(horaRepo.findByOperadorIdOrNombre(anyLong(), anyString())).thenReturn(List.of());
        when(horaService.guardar(anyLong(), any())).thenAnswer(i -> {
            HoraTrabajada h = i.getArgument(1);
            h.setId(99L);
            // como el servicio real: el horómetro de la máquina sube hasta el final de las horas
            ex.setHorometroActual(h.getHorometroInicio() + h.getHoras());
            return h;
        });

        doAnswer(i -> {
            long chat = i.getArgument(0);
            (chat == CHAT_OP ? mensajes : alDueno).add(i.getArgument(1));
            if (chat == CHAT_OP) botones.add(i.getArgument(2));
            return null;
        }).when(api).enviar(anyLong(), anyString(), any());
        doAnswer(i -> { alDueno.add(i.getArgument(2)); botones.add(i.getArgument(3)); return null; })
                .when(api).enviarFoto(eq(CHAT_DUENO), any(), anyString(), any());
    }

    Maquina maquina(long id, String nombre, double horo) {
        Maquina m = new Maquina();
        m.setId(id); m.setUsuarioId(7L); m.setNombre(nombre); m.setOperadorNombre("Luis Pérez");
        m.setHorometroActual(horo); m.setValorHoraMaquina(210000); m.setValorHoraOperador(30000);
        return m;
    }

    String ultimo() { return mensajes.get(mensajes.size() - 1); }
    LecturaHorometro lectura() { return bd.values().iterator().next(); }
    void toca(long chat, String dato) { svc.procesarCallback(chat, 1L, "cb", dato); }
    String boton(String accion) {
        for (List<Boton> fila : botones.get(botones.size() - 1)) for (Boton b : fila) if (b.dato().contains(accion)) return b.dato();
        fail("No hay botón " + accion);
        return null;
    }
    int horasRegistradas() { return mockingDetails(horaService).getInvocations().size(); }

    @Test
    void diaNormal_confirmaYRegistraLasHorasMedidas() {
        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 8420.5, 96);
        assertEquals(LecturaHorometro.CONFIRMAR, lectura().getEstado());
        assertTrue(ultimo().contains("8,0 h"), ultimo());

        toca(CHAT_OP, boton("lh:si"));

        assertEquals(LecturaHorometro.REGISTRADA, lectura().getEstado());
        ArgumentCaptor<HoraTrabajada> h = ArgumentCaptor.forClass(HoraTrabajada.class);
        verify(horaService).guardar(eq(7L), h.capture());
        assertEquals(8.0, h.getValue().getHoras(), 0.001);
        assertEquals(8412.5, h.getValue().getHorometroInicio(), 0.001);
        assertEquals(8420.5, ex.getHorometroActual(), 0.001);
        verify(ingresoService).guardar(eq(7L), any(Ingreso.class));
        assertTrue(ultimo().contains("registradas"));
    }

    @Test
    void fotoDudosa_pideConfirmarElNumeroYLuegoRegistra() {
        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 8420.5, 62);
        assertTrue(ultimo().contains("No veo bien"), ultimo());
        toca(CHAT_OP, boton("lh:si"));
        assertEquals(LecturaHorometro.REGISTRADA, lectura().getEstado());
    }

    @Test
    void lecturaMenorQueLaAnterior_pideElNumeroYNoRegistra() {
        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 8402.0, 97);
        assertEquals(LecturaHorometro.ESPERA_NUMERO, lectura().getEstado());
        assertTrue(ultimo().contains("menor"), ultimo());
        verify(horaService, never()).guardar(anyLong(), any());

        assertTrue(svc.procesarTexto(CHAT_OP, op, "8.420,5"));
        assertEquals(LecturaHorometro.CONFIRMAR, lectura().getEstado());
        assertTrue(lectura().isCorregida());
        toca(CHAT_OP, boton("lh:si"));
        assertEquals(LecturaHorometro.REGISTRADA, lectura().getEstado());
        assertEquals(8420.5, ex.getHorometroActual(), 0.001);
    }

    @Test
    void textoQueNoEsUnNumero_noSeConsume() {
        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 8402.0, 97);
        assertFalse(svc.procesarTexto(CHAT_OP, op, "8 horas"));
        assertFalse(svc.procesarTexto(CHAT_OP, op, "⏱ Registrar horas"));
        assertEquals(LecturaHorometro.ESPERA_NUMERO, lectura().getEstado());
    }

    @Test
    void saltoGrande_quedaEnRevisionYNoSeRegistraHastaQueElDuenoApruebe() {
        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 8452.0, 97);
        assertTrue(ultimo().contains("máximo"), ultimo());
        toca(CHAT_OP, boton("lh:rev"));

        assertEquals(LecturaHorometro.REVISION, lectura().getEstado());
        verify(horaService, never()).guardar(anyLong(), any());
        assertEquals(1, alDueno.size(), "el dueño recibe el aviso por Telegram");
        assertTrue(alDueno.get(0).contains("39,5"), alDueno.get(0));
        assertTrue(boton("ap:x").startsWith("ap:x:"));

        toca(CHAT_DUENO, boton("ap:x"));

        assertEquals(LecturaHorometro.REGISTRADA, lectura().getEstado());
        ArgumentCaptor<HoraTrabajada> h = ArgumentCaptor.forClass(HoraTrabajada.class);
        verify(horaService).guardar(eq(7L), h.capture());
        assertEquals(39.5, h.getValue().getHoras(), 0.001);
        assertEquals(8452.0, ex.getHorometroActual(), 0.001);
    }

    @Test
    void rechazar_noRegistraYAvisaAlOperador() {
        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 8452.0, 97);
        toca(CHAT_OP, boton("lh:rev"));
        toca(CHAT_DUENO, boton("re:x"));

        assertEquals(LecturaHorometro.RECHAZADA, lectura().getEstado());
        verify(horaService, never()).guardar(anyLong(), any());
        assertTrue(ultimo().contains("no aprobó"), ultimo());
        assertEquals(8412.5, ex.getHorometroActual(), 0.001);
    }

    @Test
    void soloElDuenoDeLaEmpresaPuedeAprobar() {
        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 8452.0, 97);
        toca(CHAT_OP, boton("lh:rev"));
        String ap = boton("ap:x");

        toca(CHAT_OP, ap);   // el propio operador intenta aprobarse
        toca(999L, ap);      // un desconocido también

        assertEquals(LecturaHorometro.REVISION, lectura().getEstado());
        verify(horaService, never()).guardar(anyLong(), any());
    }

    @Test
    void sinDuenoVinculado_laLecturaIgualQuedaEnRevisionSinRegistrar() {
        dueno.setTelegramChatId(null);
        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 8452.0, 97);
        toca(CHAT_OP, boton("lh:rev"));
        assertEquals(LecturaHorometro.REVISION, lectura().getEstado());
        assertTrue(alDueno.isEmpty());
        verify(horaService, never()).guardar(anyLong(), any());
    }

    @Test
    void variasMaquinas_preguntaDeCualEs() {
        Maquina re = maquina(2L, "Retroexcavadora 416", 6080.0);
        when(maquinaRepo.findByUsuarioIdAndOperadorNombre(7L, "Luis Pérez")).thenReturn(List.of(ex, re));
        when(maquinaRepo.findById(2L)).thenReturn(Optional.of(re));

        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 6087.5, 96);
        assertEquals(LecturaHorometro.ELEGIR_MAQUINA, lectura().getEstado());
        assertEquals(2, botones.get(botones.size() - 1).size());

        toca(CHAT_OP, "lh:maq:" + lectura().getId() + ":2");
        assertEquals("Retroexcavadora 416", lectura().getMaquinaNombre());
        assertTrue(ultimo().contains("7,5 h"), ultimo());
    }

    @Test
    void igualALaAnterior_noSeRegistra() {
        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 8412.5, 96);
        assertEquals(LecturaHorometro.CANCELADA, lectura().getEstado());
        verify(horaService, never()).guardar(anyLong(), any());
    }

    @Test
    void fotoNuevaCancelaLaQueEstabaAMedias() {
        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 8402.0, 97);
        svc.abrir(CHAT_OP, op, "foto2", new byte[]{2}, 8420.5, 97);
        long canceladas = bd.values().stream().filter(l -> LecturaHorometro.CANCELADA.equals(l.getEstado())).count();
        assertEquals(1, canceladas);
    }

    @Test
    void sinMaquinaAsignada_avisaYCancela() {
        when(maquinaRepo.findByUsuarioIdAndOperadorNombre(7L, "Luis Pérez")).thenReturn(List.of());
        svc.abrir(CHAT_OP, op, "foto1", new byte[]{1}, 8420.5, 96);
        assertEquals(LecturaHorometro.CANCELADA, lectura().getEstado());
        assertTrue(ultimo().contains("máquina asignada"));
    }

    @Test
    void numerosEscritosALaColombiana() {
        assertEquals(8420.5, HorometroService.parsear("8.420,5"), 0.001);
        assertEquals(8420.5, HorometroService.parsear("8420,5"), 0.001);
        assertEquals(8420.5, HorometroService.parsear("8420.5"), 0.001);
        assertEquals(8420.0, HorometroService.parsear("8.420"), 0.001);
    }
}
