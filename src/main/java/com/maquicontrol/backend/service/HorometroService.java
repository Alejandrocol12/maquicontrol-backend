package com.maquicontrol.backend.service;

import com.maquicontrol.backend.model.*;
import com.maquicontrol.backend.repository.*;
import com.maquicontrol.backend.service.TelegramApi.Boton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Foto del horómetro por Telegram: el operador manda la foto al terminar el día, la IA lee el número,
// las horas salen de restar el horómetro anterior y lo dudoso queda en revisión hasta que el dueño lo apruebe.
@Service
public class HorometroService {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final List<String> ABIERTAS = List.of(
            LecturaHorometro.ELEGIR_MAQUINA, LecturaHorometro.CONFIRMAR, LecturaHorometro.ESPERA_NUMERO);

    @Value("${horometro.max-horas-dia:16}") private double maxHoras;
    @Value("${horometro.confianza-minima:85}") private int confianzaMinima;
    @Value("${horometro.retencion-dias:60}") private int retencionDias;

    @Autowired private LecturaHorometroRepository lecturaRepo;
    @Autowired private MaquinaRepository maquinaRepo;
    @Autowired private OperadorRepository operadorRepo;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private HoraTrabajadaRepository horaRepo;
    @Autowired private HoraTrabajadaService horaService;
    @Autowired private IngresoService ingresoService;
    @Autowired private TelegramApi api;
    @Autowired private LectorHorometro lector;

    // ── Utilidades ────────────────────────────────────────────

    private static double r1(double x) { return Math.round(x * 10) / 10.0; }
    private static String n1(double v) { return String.format(new Locale("es", "CO"), "%,.1f", v); }
    private static String esc(String t) { return t == null ? "" : t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private static Boton b(String texto, String accion, Long id) { return new Boton(texto, "lh:" + accion + ":" + id); }
    private static Boton b(String texto, String accion, Long id, Long extra) { return new Boton(texto, "lh:" + accion + ":" + id + ":" + extra); }

    private LecturaHorometro guardar(LecturaHorometro l) { return lecturaRepo.save(l); }

    private void cerrar(LecturaHorometro l, String estado) {
        l.setEstado(estado);
        l.setResuelta(LocalDateTime.now());
        guardar(l);
    }

    // ── Entrada: llega una foto ───────────────────────────────

    // Devuelve true si la foto era del horómetro (o no se reconoció) y ya se atendió.
    // Devuelve false si debe seguir el flujo de facturas de siempre.
    public boolean procesarFoto(long chatId, Operador op, String fileId) {
        if (!lector.disponible() || !api.activo()) return false;
        byte[] original = api.descargar(fileId);
        if (original == null) return false;
        byte[] jpeg = LectorHorometro.reducir(original);
        LectorHorometro.Analisis a = lector.leer(jpeg);
        if (a == null || "factura".equals(a.tipo())) return false;
        if (!"horometro".equals(a.tipo())) {
            api.enviar(chatId, "No reconozco la foto como un horómetro ni como una factura.\nMándame una foto del <b>horómetro</b> de tu máquina, bien de frente y con buena luz.", null);
            return true;
        }
        return abrir(chatId, op, fileId, jpeg, a.lectura(), a.confianza());
    }

    // Parte desde un análisis ya hecho (se usa también en las pruebas)
    public boolean abrir(long chatId, Operador op, String fileId, byte[] foto, Double lectura, int confianza) {
        for (LecturaHorometro vieja : lecturaRepo.findByOperadorIdAndEstadoIn(op.getId(), ABIERTAS)) cerrar(vieja, LecturaHorometro.CANCELADA);

        LecturaHorometro l = new LecturaHorometro();
        l.setUsuarioId(op.getUsuarioId());
        l.setOperadorId(op.getId());
        l.setOperadorNombre(op.getNombre());
        l.setChatId(chatId);
        l.setLectura(lectura);
        l.setConfianza(confianza);
        l.setFotoFileId(fileId);
        l.setFoto(foto);
        l.setEstado(LecturaHorometro.CONFIRMAR);
        l.setCreada(LocalDateTime.now());
        l = guardar(l);
        continuar(l, op);
        return true;
    }

    // ── Entrada: el operador escribe el número ────────────────

    // true si el mensaje era el número que se estaba esperando
    public boolean procesarTexto(long chatId, Operador op, String texto) {
        Optional<LecturaHorometro> abierta = lecturaRepo.findFirstByOperadorIdAndEstadoInOrderByIdDesc(op.getId(), List.of(LecturaHorometro.ESPERA_NUMERO));
        if (abierta.isEmpty()) return false;
        if (texto == null || !texto.matches("^\\s*[\\d.,]+\\s*$")) return false; // otra cosa: sigue el flujo normal
        Double n = parsear(texto);
        if (n == null || n <= 0) {
            api.enviar(chatId, "No entendí ese número. Escríbelo así: <b>8420,5</b>", null);
            return true;
        }
        LecturaHorometro l = abierta.get();
        l.setLectura(n);
        l.setConfianza(100);
        l.setCorregida(true);
        l.setConfirmada(false);
        l.setEstado(LecturaHorometro.CONFIRMAR);
        l = guardar(l);
        continuar(l, op);
        return true;
    }

    public static Double parsear(String texto) {
        String s = texto.trim().replace(" ", "");
        try {
            if (s.contains(",")) s = s.replace(".", "").replace(",", ".");
            else if (s.matches("^\\d{1,3}(\\.\\d{3})+$")) s = s.replace(".", "");
            return Double.parseDouble(s);
        } catch (Exception e) { return null; }
    }

    // ── Entrada: se toca un botón ─────────────────────────────

    public void procesarCallback(long chatId, long messageId, String callbackId, String dato) {
        api.responderCallback(callbackId, null);
        String[] p = dato.split(":");
        if (p.length < 3) return;
        Long id;
        try { id = Long.parseLong(p[2]); } catch (NumberFormatException e) { return; }
        Optional<LecturaHorometro> opt = lecturaRepo.findById(id);
        if (opt.isEmpty()) return;
        LecturaHorometro l = opt.get();

        if ("lh".equals(p[0])) {
            if (l.getChatId() == null || l.getChatId() != chatId) return;
            api.quitarBotones(chatId, messageId);
            Operador op = operadorRepo.findById(l.getOperadorId()).orElse(null);
            if (op == null || !ABIERTAS.contains(l.getEstado())) return;
            switch (p[1]) {
                case "si" -> { l.setConfirmada(true); guardar(l); continuar(l, op); }
                case "no" -> {
                    l.setEstado(LecturaHorometro.ESPERA_NUMERO);
                    guardar(l);
                    api.enviar(chatId, "Escribe el número que marca el horómetro (por ejemplo <b>8420,5</b>).", null);
                }
                case "rev" -> { l.setConfirmada(true); guardar(l); aRevision(l); }
                case "maq" -> {
                    if (p.length < 4) return;
                    try {
                        Long maqId = Long.parseLong(p[3]);
                        Optional<Maquina> m = maquinaRepo.findById(maqId)
                                .filter(x -> x.getUsuarioId().equals(l.getUsuarioId()) && op.getNombre().equals(x.getOperadorNombre()));
                        if (m.isEmpty()) return;
                        l.setMaquinaId(m.get().getId());
                        l.setMaquinaNombre(m.get().getNombre());
                        l.setEstado(LecturaHorometro.CONFIRMAR);
                        guardar(l);
                        continuar(l, op);
                    } catch (NumberFormatException ignored) {}
                }
                default -> {}
            }
        } else if ("ap".equals(p[0]) || "re".equals(p[0])) {
            Optional<Usuario> dueno = usuarioRepo.findByTelegramChatId(chatId);
            if (dueno.isEmpty() || !dueno.get().getId().equals(l.getUsuarioId())) return;
            api.quitarBotones(chatId, messageId);
            if (!LecturaHorometro.REVISION.equals(l.getEstado())) {
                api.enviar(chatId, "Esta lectura ya fue resuelta.", null);
                return;
            }
            if ("ap".equals(p[0])) aprobar(l); else rechazar(l);
        }
    }

    // ── Máquina, lectura y reglas ─────────────────────────────

    private void continuar(LecturaHorometro l, Operador op) {
        long chat = l.getChatId();
        if (l.getMaquinaId() == null) {
            List<Maquina> asignadas = maquinaRepo.findByUsuarioIdAndOperadorNombre(op.getUsuarioId(), op.getNombre());
            if (asignadas.isEmpty()) {
                cerrar(l, LecturaHorometro.CANCELADA);
                api.enviar(chat, "No tienes una máquina asignada. Pide a tu administrador que te la asigne y vuelve a mandar la foto.", null);
                return;
            }
            if (asignadas.size() > 1) {
                l.setEstado(LecturaHorometro.ELEGIR_MAQUINA);
                guardar(l);
                api.enviar(chat, "Tienes varias máquinas asignadas. ¿De cuál es esta lectura?",
                        asignadas.stream().map(m -> List.of(b(m.getNombre(), "maq", l.getId(), m.getId()))).toList());
                return;
            }
            l.setMaquinaId(asignadas.get(0).getId());
            l.setMaquinaNombre(asignadas.get(0).getNombre());
            guardar(l);
        }
        if (l.getLectura() == null) {
            l.setEstado(LecturaHorometro.ESPERA_NUMERO);
            guardar(l);
            api.enviar(chat, "No pude leer el número en la foto. Escríbelo tú (por ejemplo <b>8420,5</b>) o manda otra foto más cerca y con buena luz.", null);
            return;
        }
        evaluar(l, op);
    }

    private void evaluar(LecturaHorometro l, Operador op) {
        long chat = l.getChatId();
        Maquina m = maquinaRepo.findById(l.getMaquinaId()).orElse(null);
        if (m == null) { cerrar(l, LecturaHorometro.CANCELADA); return; }

        double anterior = m.getHorometroActual();
        double lectura = l.getLectura();
        double delta = r1(lectura - anterior);
        l.setAnterior(anterior);
        l.setHoras(delta);

        // La IA no está segura: se confirma el número antes de seguir
        if (!l.isCorregida() && !l.isConfirmada() && l.getConfianza() != null && l.getConfianza() < confianzaMinima) {
            l.setEstado(LecturaHorometro.CONFIRMAR);
            guardar(l);
            api.enviar(chat, "No veo bien el número. ¿Dice <b>" + n1(lectura) + "</b>?",
                    List.of(List.of(b("✅ Sí, es ese", "si", l.getId()), b("✏️ No, es otro", "no", l.getId()))));
            return;
        }
        // Menor que la última lectura: casi seguro un error
        if (lectura < anterior) {
            l.setEstado(LecturaHorometro.ESPERA_NUMERO);
            guardar(l);
            api.enviar(chat, "Esa lectura (<b>" + n1(lectura) + "</b>) es <b>menor</b> que la última registrada (<b>" + n1(anterior)
                    + "</b>). Puede ser un error al leer.\nEscribe el número correcto o manda otra foto.", null);
            return;
        }
        if (delta < 0.05) {
            cerrar(l, LecturaHorometro.CANCELADA);
            api.enviar(chat, "La lectura es igual a la anterior (<b>" + n1(anterior) + "</b>), así que hoy no se registran horas. Si trabajaste, revisa que la foto sea del tablero de hoy.", null);
            return;
        }
        // Más horas de las que caben en un día: el operador debe confirmar y el dueño aprobar
        if (delta > maxHoras) {
            l.setEstado(LecturaHorometro.CONFIRMAR);
            guardar(l);
            api.enviar(chat, "Son <b>" + n1(delta) + " h</b> en un día y el máximo es <b>" + n1(maxHoras) + " h</b>.\n¿La lectura <b>" + n1(lectura) + "</b> es correcta?",
                    List.of(List.of(b("✅ Es correcta, avisar", "rev", l.getId()), b("✏️ Corregir", "no", l.getId()))));
            return;
        }
        if (!l.isConfirmada()) {
            l.setEstado(LecturaHorometro.CONFIRMAR);
            guardar(l);
            api.enviar(chat, "Leí <b>" + n1(lectura) + " h</b>. Desde " + n1(anterior) + " son <b>" + n1(delta) + " h</b> en la <b>" + esc(m.getNombre()) + "</b>.\n¿Es correcto?",
                    List.of(List.of(b("✅ Sí", "si", l.getId()), b("✏️ Corregir", "no", l.getId()))));
            return;
        }
        registrar(l, op);
    }

    // ── Registro, revisión y aprobación ───────────────────────

    // Crea las horas y el ingreso como si el operador los hubiera escrito, con el horómetro medido
    void registrar(LecturaHorometro l, Operador op) {
        long chat = l.getChatId();
        Maquina m = maquinaRepo.findById(l.getMaquinaId()).orElse(null);
        if (m == null) { cerrar(l, LecturaHorometro.CANCELADA); return; }
        double delta = r1(l.getLectura() - m.getHorometroActual());
        if (delta <= 0) {
            cerrar(l, LecturaHorometro.CANCELADA);
            api.enviar(chat, "La lectura <b>" + n1(l.getLectura()) + "</b> ya no está por encima del horómetro actual (<b>" + n1(m.getHorometroActual()) + "</b>), así que no se registró.", null);
            return;
        }
        LocalDate hoy = LocalDate.now(ZONA);

        HoraTrabajada hora = new HoraTrabajada();
        hora.setOperadorId(op.getId());
        hora.setOperadorNombre(op.getNombre());
        hora.setHoras(delta);
        hora.setFecha(hoy);
        hora.setMaquinaId(m.getId());
        hora.setMaquinaNombre(m.getNombre());
        hora.setHorometroInicio(m.getHorometroActual());
        double valorOp = m.getValorHoraOperador() > 0
                ? m.getValorHoraOperador()
                : horaRepo.findByOperadorIdOrNombre(op.getId(), op.getNombre()).stream()
                        .filter(h -> h.getValorHora() > 0).mapToDouble(HoraTrabajada::getValorHora).max().orElse(0.0);
        hora.setValorHora(valorOp);
        HoraTrabajada guardada = horaService.guardar(op.getUsuarioId(), hora);

        if (m.getValorHoraMaquina() > 0) {
            Ingreso ing = new Ingreso();
            ing.setMaquinaNombre(m.getNombre());
            ing.setTipoTrabajo("Horas");
            ing.setCantidad(delta);
            ing.setValorUnitario(m.getValorHoraMaquina());
            ing.setFecha(hoy);
            ing.setDescripcion(n1(delta) + "h — " + m.getNombre() + " (foto del horómetro)");
            ingresoService.guardar(op.getUsuarioId(), ing);
        }

        l.setHoras(delta);
        l.setHoraTrabajadaId(guardada != null ? guardada.getId() : null);
        cerrar(l, LecturaHorometro.REGISTRADA);
        api.enviar(chat, "✅ <b>" + n1(delta) + " h registradas</b>\n🚜 " + esc(m.getNombre()) + "\n📟 Horómetro: <b>" + n1(l.getLectura()) + "</b>\n📸 Foto guardada como prueba.", null);
    }

    // La lectura no se registra: queda esperando al dueño. Devuelve true si se le pudo avisar por Telegram.
    boolean aRevision(LecturaHorometro l) {
        l.setEstado(LecturaHorometro.REVISION);
        guardar(l);
        api.enviar(l.getChatId(), "Quedó <b>en revisión</b>. Tu administrador la aprobará y te aviso.", null);

        Optional<Usuario> dueno = usuarioRepo.findById(l.getUsuarioId());
        if (dueno.isEmpty() || dueno.get().getTelegramChatId() == null) return false;
        enviarARevisar(dueno.get().getTelegramChatId(), l, "⚠️ <b>Lectura en revisión</b>\n");
        return true;
    }

    private void enviarARevisar(long chatDueno, LecturaHorometro l, String titulo) {
        String texto = titulo
                + "👷 " + esc(l.getOperadorNombre()) + "\n🚜 " + esc(l.getMaquinaNombre()) + "\n"
                + "📟 Lectura <b>" + n1(l.getLectura()) + "</b> (anterior " + n1(l.getAnterior()) + ")\n"
                + "⏱ <b>" + n1(l.getHoras()) + " h</b> en un día · máximo " + n1(maxHoras) + " h\n"
                + (l.isCorregida() ? "✎ El número lo escribió el operador.\n" : "")
                + "\nNo se registra hasta que la apruebes.";
        List<List<Boton>> botones = List.of(List.of(
                new Boton("✅ Aprobar", "ap:x:" + l.getId()), new Boton("❌ Rechazar", "re:x:" + l.getId())));
        if (l.getFotoFileId() != null) api.enviarFoto(chatDueno, l.getFotoFileId(), texto, botones);
        else api.enviar(chatDueno, texto, botones);
    }

    private void aprobar(LecturaHorometro l) {
        Operador op = operadorRepo.findById(l.getOperadorId()).orElse(null);
        long chatDueno = usuarioRepo.findById(l.getUsuarioId()).map(Usuario::getTelegramChatId).orElse(0L);
        if (op == null) { cerrar(l, LecturaHorometro.CANCELADA); return; }
        registrar(l, op);
        if (chatDueno != 0) {
            api.enviar(chatDueno, LecturaHorometro.REGISTRADA.equals(l.getEstado())
                    ? "✅ Aprobada: <b>" + n1(l.getHoras()) + " h</b> registradas en la " + esc(l.getMaquinaNombre()) + "."
                    : "No se pudo registrar: el horómetro de la máquina cambió y la lectura ya no encaja.", null);
        }
    }

    private void rechazar(LecturaHorometro l) {
        cerrar(l, LecturaHorometro.RECHAZADA);
        api.enviar(l.getChatId(), "❌ Tu administrador no aprobó la lectura <b>" + n1(l.getLectura()) + "</b>. Revisa el horómetro y manda otra foto.", null);
        usuarioRepo.findById(l.getUsuarioId()).map(Usuario::getTelegramChatId)
                .ifPresent(c -> api.enviar(c, "❌ Rechazada. Le avisé a " + esc(l.getOperadorNombre()) + ".", null));
    }

    // ── El dueño: vincular su Telegram y ver lo pendiente ─────

    public boolean vincularDueno(long chatId, String codigo) {
        if (codigo == null || codigo.isBlank()) return false;
        Optional<Usuario> u = usuarioRepo.findByTelegramLinkCode(codigo.trim().toUpperCase());
        if (u.isEmpty()) return false;
        Usuario dueno = u.get();
        if (dueno.getTelegramLinkExpira() == null || dueno.getTelegramLinkExpira().isBefore(LocalDateTime.now())) return false;
        dueno.setTelegramChatId(chatId);
        dueno.setTelegramLinkCode(null);
        dueno.setTelegramLinkExpira(null);
        usuarioRepo.save(dueno);
        api.enviar(chatId, "✅ <b>Telegram vinculado</b>, " + esc(dueno.getNombre()) + ".\nAquí te aviso cuando una lectura del horómetro quede en revisión y la apruebas con un toque.\n\nEscribe /pendientes para ver lo que esté esperando.", null);
        return true;
    }

    public boolean esDueno(long chatId) { return usuarioRepo.findByTelegramChatId(chatId).isPresent(); }

    public void pendientes(long chatId) {
        Optional<Usuario> u = usuarioRepo.findByTelegramChatId(chatId);
        if (u.isEmpty()) return;
        List<LecturaHorometro> lista = lecturaRepo.findByUsuarioIdAndEstadoOrderByIdDesc(u.get().getId(), LecturaHorometro.REVISION);
        if (lista.isEmpty()) { api.enviar(chatId, "No tienes lecturas por revisar. 👌", null); return; }
        for (LecturaHorometro l : lista) enviarARevisar(chatId, l, "⚠️ <b>Lectura en revisión</b>\n");
    }

    // ── Mantenimiento diario ──────────────────────────────────

    @Scheduled(cron = "0 15 3 * * *", zone = "America/Bogota")
    public void limpiar() {
        int fotos = lecturaRepo.purgarFotosAnterioresA(LocalDateTime.now().minusDays(retencionDias));
        int abandonadas = lecturaRepo.cancelarAbandonadas(LocalDateTime.now().minusHours(24), LocalDateTime.now());
        if (fotos > 0 || abandonadas > 0)
            System.out.println("[Horómetro] Fotos borradas por retención: " + fotos + " · lecturas abandonadas: " + abandonadas);
    }
}
