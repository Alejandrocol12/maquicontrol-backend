package com.maquicontrol.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maquicontrol.backend.model.*;
import com.maquicontrol.backend.repository.*;
import com.maquicontrol.backend.service.CombustibleService;
import com.maquicontrol.backend.service.GastoService;
import com.maquicontrol.backend.service.HoraTrabajadaService;
import com.maquicontrol.backend.service.IngresoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/telegram")
public class TelegramController {

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Autowired private OperadorRepository operadorRepo;
    @Autowired private MaquinaRepository maquinaRepo;
    @Autowired private HoraTrabajadaRepository horaRepo;
    @Autowired private IngresoRepository ingresoRepo;
    @Autowired private GastoRepository gastoRepo;
    @Autowired private CorteRepository corteRepo;
    @Autowired private FaenaRepository faenaRepo;
    @Autowired private PagoOperadorRepository pagoRepo;
    @Autowired private CombustibleRepository combustibleRepo;
    @Autowired private NovedadRepository novedadRepo;
    @Autowired private MantenimientoRepository mantenimientoRepo;
    @Autowired private HoraTrabajadaService horaService;
    @Autowired private GastoService gastoService;
    @Autowired private IngresoService ingresoService;
    @Autowired private CombustibleService combustibleService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    // ── Teclado fijo (3 x 3) ───────────────────────────────────────
    private static final String BTN_HORAS    = "⏱ Horas";
    private static final String BTN_GASTO    = "💸 Gasto";
    private static final String BTN_FACTURA  = "📸 Factura";
    private static final String BTN_CORTES   = "✂️ Cortes";
    private static final String BTN_SALDO    = "💰 Mi saldo";
    private static final String BTN_RESUMEN  = "📊 Resumen";
    private static final String BTN_TANQUEO  = "⛽ Tanqueo";
    private static final String BTN_NOVEDAD  = "🛠 Novedad";
    private static final String BTN_MAQUINA  = "🚜 Máquina";
    private static final String BTN_AYUDA    = "ℹ️ Ayuda";

    private static final String URL_APP = "https://www.moventra.sbs";
    private static final String DIV = "━━━━━━━━━━━━━━━━━━";
    private static final Locale ES = Locale.forLanguageTag("es-CO");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter F_CORTA = DateTimeFormatter.ofPattern("d MMM", ES);
    private static final DateTimeFormatter F_LARGA = DateTimeFormatter.ofPattern("d MMM yyyy", ES);
    private static final String[] DIAS = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private static final Map<String, String> CATEGORIAS_GASTO = new LinkedHashMap<>();
    private static final Map<String, String> TIPOS_NOVEDAD = new LinkedHashMap<>();
    static {
        CATEGORIAS_GASTO.put("comb", "Combustible");
        CATEGORIAS_GASTO.put("mant", "Mantenimiento");
        CATEGORIAS_GASTO.put("resp", "Repuestos");
        CATEGORIAS_GASTO.put("mano", "Mano de obra");
        CATEGORIAS_GASTO.put("trans", "Transporte");
        CATEGORIAS_GASTO.put("otro", "Otro");
        TIPOS_NOVEDAD.put("falla", "Falla mecánica");
        TIPOS_NOVEDAD.put("fuga", "Fuga de aceite");
        TIPOS_NOVEDAD.put("elec", "Falla eléctrica");
        TIPOS_NOVEDAD.put("oruga", "Oruga o llantas");
        TIPOS_NOVEDAD.put("cabina", "Cabina o vidrios");
        TIPOS_NOVEDAD.put("otra", "Otra");
    }

    // Facturas pendientes cuando la IA no pudo leer el monto
    // clave: chatId → {data, nombre}
    private static class FacturaPendiente { byte[] data; String nombre; }
    private final java.util.concurrent.ConcurrentHashMap<Long, FacturaPendiente> facturasPendientes = new java.util.concurrent.ConcurrentHashMap<>();

    // Lo que el bot espera que el operador escriba a continuación (monto, galones, descripción...)
    private static class Estado { String tipo; String dato; Estado(String tipo, String dato) { this.tipo = tipo; this.dato = dato; } }
    private final java.util.concurrent.ConcurrentHashMap<Long, Estado> estados = new java.util.concurrent.ConcurrentHashMap<>();
    // Máquina que el operador eligió con "Cambiar máquina"
    private final java.util.concurrent.ConcurrentHashMap<Long, Long> maquinaElegida = new java.util.concurrent.ConcurrentHashMap<>();
    // Chats a los que ya se les mandó el teclado nuevo desde que arrancó el servidor
    private final Set<Long> tecladoAlDia = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static LocalDate hoy() { return LocalDate.now(BOGOTA); }

    // ── Webhook principal ──────────────────────────────────────────

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload) {
        try {
            JsonNode update = mapper.readTree(payload);

            JsonNode cb = update.path("callback_query");
            if (!cb.isMissingNode()) {
                handleCallback(cb);
                return ResponseEntity.ok("ok");
            }

            JsonNode msg = update.path("message");
            if (msg.isMissingNode()) return ResponseEntity.ok("ok");

            long chatId = msg.path("chat").path("id").asLong();
            String text = msg.path("text").asText("").trim();
            JsonNode photos   = msg.path("photo");
            JsonNode document = msg.path("document");

            Operador operador = operadorRepo.findByTelegramChatId(chatId).orElse(null);

            // /start siempre se atiende aunque no esté vinculado
            if (text.toLowerCase().startsWith("/start")) {
                handleStart(chatId, text, operador);
                return ResponseEntity.ok("ok");
            }

            if (operador == null) {
                enviar(chatId, "⚙ *Moventra*\n\nNo estás vinculado.\nPide el código a tu administrador y envía:\n*/start CODIGO*");
                return ResponseEntity.ok("ok");
            }

            // Foto o documento → factura
            if (!photos.isMissingNode() && photos.isArray() && photos.size() > 0) {
                handleFoto(chatId, photos, operador);
            } else if (!document.isMissingNode() && !document.isNull()) {
                handleDocumento(chatId, document, operador);
            } else {
                asegurarTeclado(chatId);
                String lower = text.toLowerCase();
                String boton = normalizarBoton(text);
                boolean esComando = lower.startsWith("/");

                // Un botón o un comando cancela lo que se estaba esperando
                if (boton != null || esComando) estados.remove(chatId);

                FacturaPendiente pendiente = facturasPendientes.get(chatId);
                Estado estado = estados.get(chatId);

                if (pendiente != null && boton == null && !esComando) {
                    // Respuesta al monto pendiente cuando la IA no pudo leer la factura
                    handleMontoPendiente(chatId, text, operador, pendiente);
                } else if (estado != null && boton == null && !esComando) {
                    handleEstado(chatId, text, operador, estado);
                } else if (BTN_HORAS.equals(boton)) {
                    promptHoras(chatId, operador);
                } else if (BTN_GASTO.equals(boton)) {
                    promptGasto(chatId);
                } else if (BTN_FACTURA.equals(boton)) {
                    enviarInline(chatId, "⚙ *Moventra*\n\n📸 Envíame la *foto o el PDF* de la factura y yo leo el monto.\n\n_Si no se lee bien, te pregunto el total._",
                        teclado(fila(btn("✖ Cancelar", "x:cancel"))));
                } else if (BTN_CORTES.equals(boton) || lower.startsWith("/cortes") || lower.equals("cortes")) {
                    handleCortes(chatId, operador);
                } else if (BTN_SALDO.equals(boton) || lower.startsWith("/saldo") || lower.equals("saldo")) {
                    handleSaldo(chatId, operador);
                } else if (BTN_RESUMEN.equals(boton) || lower.startsWith("/resumen") || lower.equals("resumen") || lower.equals("mis horas")) {
                    handleResumen(chatId, operador);
                } else if (BTN_TANQUEO.equals(boton) || lower.startsWith("/tanqueo")) {
                    promptTanqueo(chatId, operador);
                } else if (BTN_NOVEDAD.equals(boton) || lower.startsWith("/novedad")) {
                    promptNovedad(chatId, operador);
                } else if (BTN_MAQUINA.equals(boton) || lower.startsWith("/maquina") || lower.equals("mi máquina") || lower.equals("mi maquina")) {
                    handleMaquina(chatId, operador);
                } else if (BTN_AYUDA.equals(boton) || lower.equals("/menu") || lower.equals("menu") || lower.equals("menú") || lower.equals("hola") || lower.equals("ayuda") || lower.equals("/ayuda")) {
                    handleMenu(chatId, operador);
                } else if (esGasto(lower)) {
                    handleGasto(chatId, text, operador);
                } else {
                    handleHoras(chatId, text, operador);
                }
            }
        } catch (Exception e) {
            System.out.println("[TG] Error webhook: " + e.getMessage());
        }
        return ResponseEntity.ok("ok");
    }

    // Botones del teclado fijo, incluidos los de la versión anterior (el teléfono puede tener el teclado viejo)
    private String normalizarBoton(String text) {
        switch (text) {
            case "⏱ Horas": case "⏱ Registrar horas": return BTN_HORAS;
            case "💸 Gasto": case "💸 Registrar gasto": return BTN_GASTO;
            case "📸 Factura": return BTN_FACTURA;
            case "✂️ Cortes": return BTN_CORTES;
            case "💰 Mi saldo": return BTN_SALDO;
            case "📊 Resumen": case "📊 Mi resumen": return BTN_RESUMEN;
            case "⛽ Tanqueo": return BTN_TANQUEO;
            case "🛠 Novedad": return BTN_NOVEDAD;
            case "🚜 Máquina": return BTN_MAQUINA;
            case "ℹ️ Ayuda": return BTN_AYUDA;
            default: return null;
        }
    }

    private void asegurarTeclado(long chatId) {
        if (tecladoAlDia.add(chatId))
            enviarConMenu(chatId, "⌨️ *Actualicé tu menú*\nAhora tienes *Cortes*, *Mi saldo*, *Tanqueo*, *Novedad* y *Máquina* en el teclado de abajo.");
    }

    // ── Botones dentro del mensaje (callback_query) ────────────────

    private void handleCallback(JsonNode cb) {
        long chatId = cb.path("message").path("chat").path("id").asLong();
        String data = cb.path("data").asText("");
        responderCallback(cb.path("id").asText());

        Operador op = operadorRepo.findByTelegramChatId(chatId).orElse(null);
        if (op == null) {
            enviar(chatId, "⚙ *Moventra*\n\nNo estás vinculado.\nPide el código a tu administrador y envía:\n*/start CODIGO*");
            return;
        }
        String[] p = data.split(":");
        try {
            switch (p[0]) {
                case "h" -> callbackHoras(chatId, op, p);
                case "hm" -> elegirMaquina(chatId, op, p.length > 1 ? p[1] : "");
                case "g" -> callbackGasto(chatId, op, p);
                case "c" -> { if (p.length > 1 && !p[1].equals("lista")) handleCorteDetalle(chatId, op, p[1]); else handleCortes(chatId, op); }
                case "s" -> handleSaldo(chatId, op);
                case "r" -> handleResumen(chatId, op);
                case "t" -> promptTanqueo(chatId, op);
                case "n" -> callbackNovedad(chatId, op, p);
                case "m" -> handleMaquina(chatId, op);
                case "menu" -> handleMenu(chatId, op);
                case "u" -> {
                    String r = deshacer(op, p);
                    enviar(chatId, r);
                }
                case "x" -> callbackCorregir(chatId, op, p);
                default -> { /* botón desconocido */ }
            }
        } catch (Exception e) {
            System.out.println("[TG] Error callback " + data + ": " + e.getMessage());
            enviar(chatId, "⚠️ No pude completar esa acción. Intenta de nuevo.");
        }
    }

    private void callbackHoras(long chatId, Operador op, String[] p) {
        String acc = p.length > 1 ? p[1] : "menu";
        switch (acc) {
            case "menu" -> promptHoras(chatId, op);
            case "otra" -> {
                estados.put(chatId, new Estado("horas", null));
                enviarInline(chatId, "⏱ *¿Cuántas horas?*\nEscribe el número, por ejemplo _7.5_",
                    teclado(fila(btn("✖ Cancelar", "x:cancel"))));
            }
            case "maq" -> listarMaquinas(chatId, op);
            default -> {
                double horas;
                try { horas = Double.parseDouble(acc); } catch (NumberFormatException e) { return; }
                registrarHoras(chatId, op, horas, maquinaActiva(chatId, op));
            }
        }
    }

    private void callbackGasto(long chatId, Operador op, String[] p) {
        String acc = p.length > 1 ? p[1] : "menu";
        switch (acc) {
            case "menu" -> promptGasto(chatId);
            case "foto" -> enviarInline(chatId, "⚙ *Moventra*\n\n📸 Envíame la *foto o el PDF* de la factura y yo leo el monto.",
                teclado(fila(btn("✖ Cancelar", "x:cancel"))));
            case "c" -> {
                String categoria = CATEGORIAS_GASTO.get(p.length > 2 ? p[2] : "");
                if (categoria == null) return;
                estados.put(chatId, new Estado("gasto", categoria));
                enviarInline(chatId, "💸 *" + categoria + "*\nEscribe el *monto* y, si quieres, qué fue.\n\n_Ejemplos:_\n• _85000 filtro de aceite_\n• _$150.000 mano de obra_",
                    teclado(fila(btn("✖ Cancelar", "x:cancel"))));
            }
            default -> { }
        }
    }

    private void callbackNovedad(long chatId, Operador op, String[] p) {
        String acc = p.length > 1 ? p[1] : "menu";
        if ("menu".equals(acc)) { promptNovedad(chatId, op); return; }
        String tipo = TIPOS_NOVEDAD.get(acc);
        if (tipo == null) return;
        estados.put(chatId, new Estado("novedad", tipo));
        enviarInline(chatId, "🛠 *" + tipo + "*\nCuéntame en una frase qué pasó.\n\n_Si no quieres agregar nada, escribe_ *omitir*",
            teclado(fila(btn("✖ Cancelar", "x:cancel"))));
    }

    private void callbackCorregir(long chatId, Operador op, String[] p) {
        String acc = p.length > 1 ? p[1] : "";
        if ("cancel".equals(acc)) {
            estados.remove(chatId);
            enviar(chatId, "👍 Cancelado.");
            return;
        }
        // Corregir = deshacer el registro y pedirlo de nuevo
        String[] undo = p.clone();
        undo[0] = "u";
        String r = deshacer(op, undo);
        if (!r.startsWith("↩️")) { enviar(chatId, r); return; }
        enviar(chatId, r);
        switch (acc) {
            case "h" -> promptHoras(chatId, op);
            case "g" -> promptGasto(chatId);
            case "c" -> promptTanqueo(chatId, op);
            default -> { }
        }
    }

    // ── Deshacer ──────────────────────────────────────────────────

    // u:h:<horaId>:<ingresoId> · u:g:<gastoId> · u:c:<combustibleId>
    private String deshacer(Operador op, String[] p) {
        if (p.length < 3) return "⚠️ No pude deshacer ese registro.";
        long id;
        try { id = Long.parseLong(p[2]); } catch (NumberFormatException e) { return "⚠️ No pude deshacer ese registro."; }
        Long uid = op.getUsuarioId();
        LocalDate hoy = hoy();

        switch (p[1]) {
            case "h": {
                HoraTrabajada h = horaRepo.findById(id).orElse(null);
                if (h == null) return "ℹ️ Ese registro ya no existe.";
                boolean suyo = Objects.equals(h.getUsuarioId(), uid)
                    && (Objects.equals(h.getOperadorId(), op.getId()) || op.getNombre().equals(h.getOperadorNombre()));
                if (!suyo) return "⚠️ Ese registro no es tuyo.";
                if (h.getFecha() == null || !h.getFecha().equals(hoy)) return "⚠️ Solo puedes deshacer registros de hoy. Pídele a tu administrador que lo corrija.";
                boolean enCorte = corteRepo.findByUsuarioId(uid).stream().anyMatch(c ->
                    c.getFechaInicio() != null && c.getFechaFin() != null
                        && !h.getFecha().isBefore(c.getFechaInicio()) && !h.getFecha().isAfter(c.getFechaFin())
                        && (c.getMaquinaNombre() == null || c.getMaquinaNombre().equals(h.getMaquinaNombre())));
                if (enCorte) return "⚠️ Esas horas ya están dentro de un corte. Pídele a tu administrador que lo corrija.";
                horaService.eliminar(id);
                if (p.length > 3) {
                    try {
                        long ingId = Long.parseLong(p[3]);
                        if (ingId > 0) ingresoRepo.findById(ingId)
                            .filter(i -> Objects.equals(i.getUsuarioId(), uid))
                            .ifPresent(i -> ingresoService.eliminar(i.getId()));
                    } catch (NumberFormatException ignored) { }
                }
                return "↩️ *Horas deshechas.* Registro eliminado.";
            }
            case "g": {
                Gasto g = gastoRepo.findById(id).orElse(null);
                if (g == null) return "ℹ️ Ese gasto ya no existe.";
                if (!Objects.equals(g.getUsuarioId(), uid)) return "⚠️ Ese gasto no es tuyo.";
                if (g.getCorteId() != null || g.getFecha() == null || !g.getFecha().equals(hoy)) return "⚠️ Solo puedes deshacer gastos de hoy. Pídele a tu administrador que lo corrija.";
                gastoService.eliminar(id);
                return "↩️ *Gasto deshecho.* Registro eliminado.";
            }
            case "c": {
                Combustible c = combustibleRepo.findById(id).orElse(null);
                if (c == null) return "ℹ️ Ese tanqueo ya no existe.";
                if (!Objects.equals(c.getUsuarioId(), uid)) return "⚠️ Ese tanqueo no es tuyo.";
                if (c.getFecha() == null || !c.getFecha().equals(hoy)) return "⚠️ Solo puedes deshacer tanqueos de hoy. Pídele a tu administrador que lo corrija.";
                combustibleService.eliminar(id);
                return "↩️ *Tanqueo deshecho.* Registro y gasto eliminados.";
            }
            default:
                return "⚠️ No pude deshacer ese registro.";
        }
    }

    // ── /start ────────────────────────────────────────────────────

    private void handleStart(long chatId, String text, Operador existente) {
        if (existente != null) {
            enviarConMenu(chatId, "✅ Ya estás vinculado como *" + md(existente.getNombre()) + "*.\n¿Qué deseas hacer?");
            handleMenu(chatId, existente);
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            enviar(chatId, "⚙ *Moventra*\n\nPara vincular tu cuenta envía:\n*/start CODIGO*\n\nPide el código a tu administrador.");
            return;
        }
        String code = parts[1].trim().toUpperCase();
        operadorRepo.findByTelegramLinkCode(code).ifPresentOrElse(op -> {
            op.setTelegramChatId(chatId);
            op.setTelegramLinkCode(null);
            operadorRepo.save(op);
            enviarConMenu(chatId, "✅ ¡Hola *" + md(op.getNombre()) + "*!\nTu cuenta está vinculada a Moventra.\n\nUsa los botones para registrar horas y gastos, ver tus cortes y tu saldo.");
            handleMenu(chatId, op);
        }, () -> enviar(chatId, "❌ Código inválido o ya usado.\nPide un nuevo código a tu administrador."));
    }

    // ── Datos del operador ────────────────────────────────────────

    private List<HoraTrabajada> horasDe(Operador op) {
        return horaRepo.findByUsuarioIdAndOperadorIdOrNombre(op.getUsuarioId(), op.getId(), op.getNombre());
    }

    private static double sumaHoras(List<HoraTrabajada> hs, java.util.function.Predicate<HoraTrabajada> f) {
        return hs.stream().filter(f).mapToDouble(HoraTrabajada::getHoras).sum();
    }

    private static double ganadoDe(List<HoraTrabajada> hs) {
        return hs.stream().mapToDouble(h -> h.getHoras() * h.getValorHora()).sum();
    }

    private double pagadoTotal(Operador op) {
        return pagoRepo.findByUsuarioIdAndOperadorNombre(op.getUsuarioId(), op.getNombre()).stream().mapToDouble(PagoOperador::getMonto).sum();
    }

    private static class CorteVista {
        Long id; String titulo; LocalDate ini, fin; double horas, ganado, pagado; int dias; boolean enCurso; String maquina;
        double pendiente() { return Math.max(0, ganado - pagado); }
    }

    // Los cortes en los que trabajó el operador, del más viejo al más nuevo, y al final el tramo que aún no se corta.
    // Los pagos llenan primero lo ganado antes y después cada corte, igual que en la pantalla de Operadores.
    private List<CorteVista> cortesDe(Operador op) {
        Long uid = op.getUsuarioId();
        List<HoraTrabajada> horas = horasDe(op);
        double pagado = pagadoTotal(op);
        Map<Long, Faena> faenas = faenaRepo.findByUsuarioId(uid).stream().collect(Collectors.toMap(Faena::getId, f -> f, (a, b) -> a));
        List<Corte> cortes = corteRepo.findByUsuarioId(uid);
        List<CorteVista> res = new ArrayList<>();
        LocalDate ultimoFin = null;

        for (Corte c : cortes) {
            if (!c.isConOperador() || c.getFechaInicio() == null || c.getFechaFin() == null) continue;
            Faena f = faenas.get(c.getFaenaId());
            String maq = (c.getMaquinaNombre() != null && !c.getMaquinaNombre().isBlank()) ? c.getMaquinaNombre() : (f != null ? f.getMaquinaNombre() : null);
            List<HoraTrabajada> delTramo = horas.stream()
                .filter(h -> h.getFecha() != null && !h.getFecha().isBefore(c.getFechaInicio()) && !h.getFecha().isAfter(c.getFechaFin()))
                .filter(h -> maq == null || maq.equals(h.getMaquinaNombre()))
                .toList();
            if (delTramo.isEmpty()) continue;

            int numero = (int) cortes.stream()
                .filter(o -> Objects.equals(o.getFaenaId(), c.getFaenaId()) && o.getFechaInicio() != null && o.getFechaInicio().isBefore(c.getFechaInicio()))
                .count() + 1;
            double previo = ganadoDe(horas.stream().filter(h -> h.getFecha() != null && h.getFecha().isBefore(c.getFechaInicio())).toList());

            CorteVista v = new CorteVista();
            v.id = c.getId();
            v.titulo = (c.getNombre() != null && !c.getNombre().isBlank()) ? c.getNombre().trim() : "Corte " + numero;
            v.ini = c.getFechaInicio(); v.fin = c.getFechaFin(); v.maquina = maq;
            v.horas = delTramo.stream().mapToDouble(HoraTrabajada::getHoras).sum();
            v.ganado = ganadoDe(delTramo);
            v.dias = (int) delTramo.stream().map(HoraTrabajada::getFecha).distinct().count();
            v.pagado = Math.min(v.ganado, Math.max(0, pagado - previo));
            res.add(v);
            if (ultimoFin == null || c.getFechaFin().isAfter(ultimoFin)) ultimoFin = c.getFechaFin();
        }
        res.sort(Comparator.comparing((CorteVista v) -> v.ini));

        final LocalDate desde = ultimoFin;
        List<HoraTrabajada> restantes = horas.stream().filter(h -> h.getFecha() != null && (desde == null || h.getFecha().isAfter(desde))).toList();
        if (!restantes.isEmpty()) {
            double previo = desde == null ? 0 : ganadoDe(horas.stream().filter(h -> h.getFecha() != null && !h.getFecha().isAfter(desde)).toList());
            CorteVista v = new CorteVista();
            v.id = null; v.enCurso = true;
            v.titulo = "Corte " + (res.size() + 1);
            v.ini = restantes.stream().map(HoraTrabajada::getFecha).min(Comparator.naturalOrder()).orElse(hoy());
            v.fin = hoy();
            v.horas = restantes.stream().mapToDouble(HoraTrabajada::getHoras).sum();
            v.ganado = ganadoDe(restantes);
            v.dias = (int) restantes.stream().map(HoraTrabajada::getFecha).distinct().count();
            v.pagado = Math.min(v.ganado, Math.max(0, pagado - previo));
            res.add(v);
        }
        return res;
    }

    private String fechaCorta(LocalDate d) { return d.format(F_CORTA).replace(".", ""); }

    // ── Menú ──────────────────────────────────────────────────────

    private void handleMenu(long chatId, Operador op) {
        Maquina maq = maquinaActiva(chatId, op);
        List<HoraTrabajada> horas = horasDe(op);
        LocalDate hoy = hoy();
        LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
        double hoyH = sumaHoras(horas, h -> hoy.equals(h.getFecha()));
        double semana = sumaHoras(horas, h -> h.getFecha() != null && !h.getFecha().isBefore(lunes));
        CorteVista actual = cortesDe(op).stream().filter(c -> c.enCurso).findFirst().orElse(null);

        StringBuilder sb = new StringBuilder();
        sb.append("⚙ *MOVENTRA*\nHola *").append(md(op.getNombre().split(" ")[0])).append("* 👋\n").append(DIV).append("\n");
        if (maq != null) sb.append("🚜 *").append(md(maq.getNombre())).append("* · horómetro *").append(numFmt(maq.getHorometroActual())).append("*\n");
        else sb.append("🚜 _Sin máquina asignada_\n");
        sb.append("⏱ Hoy *").append(numFmt(hoyH)).append(" h* · 📅 Semana *").append(numFmt(semana)).append(" h*\n");
        if (actual != null) sb.append("✂️ Corte en curso *").append(numFmt(actual.horas)).append(" h* · *").append(TelegramUtil.dinero(actual.ganado)).append("*\n");
        sb.append(DIV).append("\n*¿Qué registramos?*\n_Tip: también puedes escribir “8 horas” o enviar la foto de una factura._");

        enviarInline(chatId, sb.toString(), teclado(
            fila(btn("⚡ Jornada de 8 h", "h:8"), btn("✏️ Otras horas", "h:otra")),
            fila(btn("💸 Gasto", "g:menu"), btn("📸 Factura", "g:foto")),
            fila(btn("✂️ Mis cortes", "c:lista"), btn("💰 Mi saldo", "s:ver")),
            fila(btn("📊 Ver mi resumen", "r:ver"))));
    }

    // ── Cortes ────────────────────────────────────────────────────

    private void handleCortes(long chatId, Operador op) {
        List<CorteVista> todos = cortesDe(op);
        if (todos.isEmpty()) {
            enviarInline(chatId, "✂️ *MIS CORTES*\n" + DIV + "\nAún no tienes horas registradas, así que no hay cortes para mostrar.",
                teclado(fila(btn("⏱ Registrar horas", "h:menu"))));
            return;
        }
        List<CorteVista> ultimos = new ArrayList<>(todos.subList(Math.max(0, todos.size() - 3), todos.size()));
        Collections.reverse(ultimos);

        StringBuilder sb = new StringBuilder("✂️ *MIS CORTES*\n_Tus últimos " + ultimos.size() + (ultimos.size() == 1 ? " corte" : " cortes") + "_\n" + DIV + "\n");
        double totalGanado = 0;
        ArrayNode botones = mapper.createArrayNode();
        for (CorteVista v : ultimos) {
            sb.append(icono(v)).append(" *").append(md(v.titulo)).append("* · ").append(fechaCorta(v.ini)).append(" → ").append(v.enCurso ? "hoy" : fechaCorta(v.fin)).append("\n");
            sb.append("    *").append(numFmt(v.horas)).append(" h* · *").append(TelegramUtil.dinero(v.ganado)).append("* · _").append(estadoTxt(v)).append("_\n");
            totalGanado += v.ganado;
            botones.add(btn(icono(v) + " " + v.titulo, "c:" + (v.enCurso ? "cur" : String.valueOf(v.id))));
        }
        sb.append(DIV).append("\nGanado en estos cortes *").append(TelegramUtil.dinero(totalGanado)).append("*\n_Toca un corte para ver el detalle._");

        ArrayNode filas = mapper.createArrayNode();
        filas.add(botones);
        filas.add(fila(btn("💰 Mi saldo", "s:ver"), btnUrl("📱 Todos en la app ↗", URL_APP)));
        enviarInline(chatId, sb.toString(), filas);
    }

    private String icono(CorteVista v) { return v.enCurso ? "🟡" : v.pendiente() < 1 ? "✅" : v.pagado > 0 ? "🟠" : "⏳"; }

    private String estadoTxt(CorteVista v) {
        if (v.enCurso) return v.pendiente() < 1 ? "en curso · pagado" : "en curso";
        if (v.pendiente() < 1) return "pagado";
        if (v.pagado > 0) return "pago parcial";
        return "por pagar";
    }

    private void handleCorteDetalle(long chatId, Operador op, String idTxt) {
        CorteVista v = cortesDe(op).stream()
            .filter(c -> idTxt.equals(c.enCurso ? "cur" : String.valueOf(c.id))).findFirst().orElse(null);
        if (v == null) { enviar(chatId, "ℹ️ No encontré ese corte. Puede que haya cambiado."); handleCortes(chatId, op); return; }

        double valorHora = v.horas > 0 ? v.ganado / v.horas : 0;
        double pct = v.ganado > 0 ? Math.min(100, v.pagado / v.ganado * 100) : 0;
        StringBuilder sb = new StringBuilder();
        sb.append("🧾 *").append(md(v.titulo.toUpperCase())).append("*").append(v.enCurso ? " · EN CURSO" : "").append("\n");
        sb.append("_").append(v.maquina != null ? "🚜 " + md(v.maquina) + " · " : "").append(fechaCorta(v.ini)).append(" → ").append(v.enCurso ? "hoy" : fechaCorta(v.fin)).append("_\n").append(DIV).append("\n");
        sb.append("⏱ *").append(numFmt(v.horas)).append(" h* en *").append(v.dias).append(v.dias == 1 ? " día" : " días").append("*");
        if (v.dias > 0) sb.append(" _· prom. ").append(String.format(ES, "%.1f", v.horas / v.dias)).append(" h/día_");
        sb.append("\n\n```\n");
        sb.append(String.format("%-17s%s\n", numFmt(v.horas) + " h × " + TelegramUtil.dinero(valorHora), TelegramUtil.dinero(v.ganado)));
        sb.append(String.format("%-17s%s\n", "Pagado", TelegramUtil.dinero(v.pagado)));
        sb.append(String.format("%-17s%s\n", "Pendiente", TelegramUtil.dinero(v.pendiente())));
        sb.append("```\n");
        sb.append(TelegramUtil.barra(pct, 100, 8, '▰', '▱')).append(" *").append(Math.round(pct)).append("% pagado*\n");

        List<PagoOperador> pagos = pagoRepo.findByUsuarioIdAndOperadorNombre(op.getUsuarioId(), op.getNombre()).stream()
            .filter(p -> v.id != null && v.id.equals(p.getCorteId()))
            .sorted(Comparator.comparing(PagoOperador::getFecha, Comparator.nullsLast(Comparator.reverseOrder()))).limit(4).toList();
        if (!pagos.isEmpty()) {
            sb.append("\n*PAGOS DE ESTE CORTE*\n");
            for (PagoOperador p : pagos)
                sb.append("💵 ").append(p.getFecha() != null ? fechaCorta(p.getFecha()) : "—").append(" · *").append(TelegramUtil.dinero(p.getMonto())).append("*\n");
        }
        sb.append(DIV).append("\n");
        sb.append(v.enCurso ? "_El corte sigue abierto: las horas que registres se suman._" : "_Los pagos se reparten del corte más viejo al más nuevo._");

        enviarInline(chatId, sb.toString(), teclado(
            fila(btn("⬅ Todos los cortes", "c:lista"), btnUrl("📱 Ver en la app ↗", URL_APP))));
    }

    // ── Saldo ─────────────────────────────────────────────────────

    private void handleSaldo(long chatId, Operador op) {
        double ganado = ganadoDe(horasDe(op));
        double pagado = pagadoTotal(op);
        double debe = Math.max(0, ganado - pagado);
        double pct = ganado > 0 ? Math.min(100, pagado / ganado * 100) : 0;

        StringBuilder sb = new StringBuilder("💰 *MI SALDO*\n" + DIV + "\n");
        sb.append("Ganado en total  *").append(TelegramUtil.dinero(ganado)).append("*\n");
        sb.append("Pagado  *").append(TelegramUtil.dinero(pagado)).append("*\n").append(DIV).append("\n");
        if (debe < 1 && pagado > ganado + 1) {
            sb.append("*TIENES UN ADELANTO*\n`").append(TelegramUtil.dinero(pagado - ganado)).append("`\n");
        } else {
            sb.append("*TE DEBEN*\n`").append(TelegramUtil.dinero(debe)).append("`\n");
            sb.append(TelegramUtil.barra(pct, 100, 10, '▰', '▱')).append(" *").append(Math.round(pct)).append("% pagado*\n");
        }
        List<PagoOperador> pagos = pagoRepo.findByUsuarioIdAndOperadorNombre(op.getUsuarioId(), op.getNombre()).stream()
            .sorted(Comparator.comparing(PagoOperador::getFecha, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(PagoOperador::getId, Comparator.reverseOrder()))
            .limit(3).toList();
        if (!pagos.isEmpty()) {
            sb.append("\n*ÚLTIMOS PAGOS*\n");
            for (PagoOperador p : pagos) {
                String desc = p.getDescripcion() == null ? "" : p.getDescripcion().trim();
                if (desc.length() > 26) desc = desc.substring(0, 25) + "…";
                sb.append("💵 ").append(p.getFecha() != null ? fechaCorta(p.getFecha()) : "—").append(" · *").append(TelegramUtil.dinero(p.getMonto())).append("*");
                if (!desc.isEmpty()) sb.append(" · ").append(md(desc));
                sb.append("\n");
            }
        } else sb.append("\n_Aún no tienes pagos registrados._\n");

        enviarInline(chatId, sb.toString(), teclado(fila(btn("✂️ Ver cortes", "c:lista"), btnUrl("📱 Abrir en la app ↗", URL_APP))));
    }

    // ── Resumen ───────────────────────────────────────────────────

    private void handleResumen(long chatId, Operador op) {
        LocalDate hoy = hoy();
        LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        List<HoraTrabajada> horas = horasDe(op);
        double semana = sumaHoras(horas, h -> h.getFecha() != null && !h.getFecha().isBefore(lunes));
        double mes = sumaHoras(horas, h -> h.getFecha() != null && !h.getFecha().isBefore(inicioMes));

        StringBuilder sb = new StringBuilder("📊 *TU RESUMEN* · " + hoy.format(DateTimeFormatter.ofPattern("MMMM", ES)).toUpperCase() + "\n" + DIV + "\n");
        sb.append("📅 Semana *").append(numFmt(semana)).append(" h* · 📆 Mes *").append(numFmt(mes)).append(" h*\n\n");
        sb.append("*ÚLTIMOS 7 DÍAS*\n```\n");
        for (int i = 6; i >= 0; i--) {
            LocalDate d = hoy.minusDays(i);
            double h = sumaHoras(horas, x -> d.equals(x.getFecha()));
            sb.append(DIAS[d.getDayOfWeek().getValue() - 1]).append(' ').append(String.format("%2d", d.getDayOfMonth())).append(' ')
              .append(TelegramUtil.barra(h, 8, 8, '█', '░')).append(' ').append(h > 0 ? numFmt(h) : (i == 0 ? "hoy" : "—")).append('\n');
        }
        sb.append("```\n");

        Map<String, Double> porMaquina = new LinkedHashMap<>();
        horas.stream().filter(h -> h.getFecha() != null && !h.getFecha().isBefore(inicioMes))
            .forEach(h -> porMaquina.merge(h.getMaquinaNombre() == null ? "Sin máquina" : h.getMaquinaNombre(), h.getHoras(), Double::sum));
        if (!porMaquina.isEmpty()) {
            double max = porMaquina.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
            sb.append("\n*POR MÁQUINA · MES*\n```\n");
            porMaquina.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(5).forEach(e -> {
                String n = e.getKey().length() > 11 ? e.getKey().substring(0, 11) : e.getKey();
                sb.append(String.format("%-11s ", n)).append(TelegramUtil.barra(e.getValue(), max, 8, '▰', '▱')).append(' ').append(numFmt(e.getValue())).append(" h\n");
            });
            sb.append("```\n");
        }
        CorteVista actual = cortesDe(op).stream().filter(c -> c.enCurso).findFirst().orElse(null);
        if (actual != null) sb.append("✂️ Corte en curso *").append(numFmt(actual.horas)).append(" h* · *").append(TelegramUtil.dinero(actual.ganado)).append("*\n");

        enviarInline(chatId, sb.toString(), teclado(fila(btn("🔄 Actualizar", "r:ver"), btnUrl("📱 Abrir en la app ↗", URL_APP))));
    }

    // ── Horas ─────────────────────────────────────────────────────

    private void promptHoras(long chatId, Operador op) {
        Maquina maq = maquinaActiva(chatId, op);
        String txt = "⏱ *REGISTRAR HORAS*\n" + (maq != null ? "🚜 *" + md(maq.getNombre()) + "* · " : "") + hoy().format(F_CORTA).replace(".", "") + "\n" + DIV
            + "\n*¿Cuántas horas trabajaste hoy?*\n_Toca una opción o escribe el número, por ejemplo 7.5_";
        enviarInline(chatId, txt, teclado(
            fila(btn("4 h", "h:4"), btn("6 h", "h:6"), btn("⚡ 8 h", "h:8")),
            fila(btn("10 h", "h:10"), btn("12 h", "h:12"), btn("✏️ Otra", "h:otra")),
            fila(btn("🔁 Cambiar máquina", "h:maq")),
            fila(btn("✖ Cancelar", "x:cancel"))));
    }

    private void listarMaquinas(long chatId, Operador op) {
        List<Maquina> maquinas = maquinaRepo.findByUsuarioId(op.getUsuarioId());
        if (maquinas.isEmpty()) { enviar(chatId, "ℹ️ Todavía no hay máquinas registradas."); return; }
        ArrayNode filas = mapper.createArrayNode();
        ArrayNode actual = mapper.createArrayNode();
        for (Maquina m : maquinas.stream().limit(10).toList()) {
            actual.add(btn("🚜 " + m.getNombre(), "hm:" + m.getId()));
            if (actual.size() == 2) { filas.add(actual); actual = mapper.createArrayNode(); }
        }
        if (actual.size() > 0) filas.add(actual);
        filas.add(fila(btn("✖ Cancelar", "x:cancel")));
        enviarInline(chatId, "🔁 *CAMBIAR MÁQUINA*\n" + DIV + "\n¿Con cuál máquina trabajas ahora?", filas);
    }

    private void elegirMaquina(long chatId, Operador op, String idTxt) {
        try {
            Maquina m = maquinaRepo.findById(Long.parseLong(idTxt)).filter(x -> Objects.equals(x.getUsuarioId(), op.getUsuarioId())).orElse(null);
            if (m == null) { enviar(chatId, "ℹ️ No encontré esa máquina."); return; }
            maquinaElegida.put(chatId, m.getId());
            enviarInline(chatId, "✅ Ahora trabajas con *" + md(m.getNombre()) + "*.\nLas horas, gastos y tanqueos que registres van a esa máquina.",
                teclado(fila(btn("⏱ Registrar horas", "h:menu"), btn("🚜 Ver máquina", "m:ver"))));
        } catch (NumberFormatException e) { enviar(chatId, "ℹ️ No encontré esa máquina."); }
    }

    private void handleHoras(long chatId, String text, Operador op) {
        ParsedHoras ph = parsearHoras(text, op);
        if (ph.horas <= 0) {
            enviarConMenu(chatId,
                "⚙ *Moventra*\n\nNo entendí las horas. Ejemplos:\n" +
                "• _8 horas_\n• _9h CAT 320_\n• _7.5 horas excavadora_");
            return;
        }
        registrarHoras(chatId, op, ph.horas, resolverMaquina(chatId, ph.maquinaMencionada, op));
    }

    private void registrarHoras(long chatId, Operador op, double horas, Maquina maq) {
        if (horas <= 0 || horas > 24) {
            enviar(chatId, "⚠️ Las horas de un día deben estar entre 0 y 24. Intenta de nuevo.");
            return;
        }
        estados.remove(chatId);

        HoraTrabajada hora = new HoraTrabajada();
        hora.setOperadorId(op.getId());
        hora.setOperadorNombre(op.getNombre());
        hora.setHoras(horas);
        hora.setFecha(hoy());
        if (maq != null) {
            hora.setMaquinaId(maq.getId());
            hora.setMaquinaNombre(maq.getNombre());
            hora.setHorometroInicio(maq.getHorometroActual());
        }
        double valorOp = (maq != null && maq.getValorHoraOperador() > 0)
            ? maq.getValorHoraOperador()
            : horaRepo.findByOperadorIdOrNombre(op.getId(), op.getNombre()).stream()
                .filter(h -> h.getValorHora() > 0).mapToDouble(HoraTrabajada::getValorHora).max().orElse(0.0);
        hora.setValorHora(valorOp);
        HoraTrabajada guardada = horaService.guardar(op.getUsuarioId(), hora);

        long ingresoId = 0;
        if (maq != null && maq.getValorHoraMaquina() > 0) {
            Ingreso ing = new Ingreso();
            ing.setMaquinaNombre(maq.getNombre());
            ing.setTipoTrabajo("Horas");
            ing.setCantidad(horas);
            ing.setValorUnitario(maq.getValorHoraMaquina());
            ing.setFecha(hoy());
            ing.setDescripcion(numFmt(horas) + "h — " + maq.getNombre() + " (Telegram)");
            Ingreso ingGuardado = ingresoService.guardar(op.getUsuarioId(), ing);
            if (ingGuardado != null && ingGuardado.getId() != null) ingresoId = ingGuardado.getId();
        }

        LocalDate lunes = hoy().with(DayOfWeek.MONDAY);
        double totalSemana = sumaHoras(horasDe(op), h -> h.getFecha() != null && !h.getFecha().isBefore(lunes));
        String maqTxt = maq != null ? md(maq.getNombre()) : "Sin máquina asignada";

        String ref = guardada.getId() + ":" + ingresoId;
        enviarInline(chatId,
            "✅ *HORAS REGISTRADAS*\n" + DIV + "\n" +
            "⏱ *" + numFmt(horas) + " h*\n" +
            "🚜 " + maqTxt + "\n" +
            "📅 " + hoy().format(F_LARGA) + "\n" +
            "📈 Acumulado semana: *" + numFmt(totalSemana) + " h*\n" + DIV +
            "\n_¿Tuviste algún gasto hoy? Envíame la foto de la factura._\n_Registro #" + guardada.getId() + "_",
            teclado(
                fila(btn("↩️ Deshacer", "u:h:" + ref), btn("✏️ Corregir", "x:h:" + ref)),
                fila(btn("💸 Registrar gasto", "g:menu"), btnUrl("📱 Ver en la app ↗", URL_APP))));
    }

    // ── Gasto ─────────────────────────────────────────────────────

    private void promptGasto(long chatId) {
        enviarInline(chatId,
            "💸 *REGISTRAR GASTO*\n" + DIV + "\n*¿Qué tipo de gasto fue?*\n\n" +
            "📸 Si tienes la factura, envíame la foto y yo leo el monto.\n" +
            "✍️ O escríbelo directo: _gasté $85.000 en filtro CAT_",
            teclado(
                fila(btn("⛽ Combustible", "g:c:comb"), btn("🔧 Mantenimiento", "g:c:mant")),
                fila(btn("🔩 Repuestos", "g:c:resp"), btn("👷 Mano de obra", "g:c:mano")),
                fila(btn("🚚 Transporte", "g:c:trans"), btn("📦 Otro", "g:c:otro")),
                fila(btn("📸 Enviar foto de la factura", "g:foto")),
                fila(btn("✖ Cancelar", "x:cancel"))));
    }

    private void handleGasto(long chatId, String text, Operador op) {
        ParsedGasto pg = parsearGasto(text, op);
        if (pg.monto <= 0) {
            enviarConMenu(chatId,
                "⚙ *Moventra*\n\nNo entendí el monto. Ejemplos:\n" +
                "• _gasté $85.000 en filtro_\n• _gasto 150000 aceite CAT_\n• _compré repuesto 75000_");
            return;
        }
        registrarGasto(chatId, op, pg.monto, pg.descripcion, pg.categoria, pg.maquinaNombre);
    }

    private void registrarGasto(long chatId, Operador op, double monto, String descripcion, String categoria, String maquinaMencionada) {
        estados.remove(chatId);
        Gasto gasto = new Gasto();
        gasto.setDescripcion(descripcion);
        gasto.setMonto(monto);
        gasto.setCategoria(categoria);
        gasto.setFecha(hoy());
        Maquina maqGasto = resolverMaquina(chatId, maquinaMencionada, op);
        if (maqGasto != null) gasto.setMaquinaNombre(maqGasto.getNombre());
        Gasto guardado = gastoService.guardar(op.getUsuarioId(), gasto);

        String maqLinea = gasto.getMaquinaNombre() != null ? "\n🚜 " + md(gasto.getMaquinaNombre()) : "";
        enviarInline(chatId,
            "✅ *GASTO REGISTRADO*\n" + DIV + "\n" +
            "💸 *" + TelegramUtil.dinero(monto) + "*\n" +
            "📝 " + md(descripcion) + maqLinea + "\n" +
            "🏷 " + md(categoria) + "\n" + DIV +
            "\n_Puedes enviar la foto de la factura ahora_\n_Registro #" + guardado.getId() + "_",
            teclado(
                fila(btn("↩️ Deshacer", "u:g:" + guardado.getId()), btn("✏️ Corregir", "x:g:" + guardado.getId())),
                fila(btn("📸 Adjuntar factura", "g:foto"), btnUrl("📱 Ver en la app ↗", URL_APP))));
    }

    // ── Tanqueo ───────────────────────────────────────────────────

    private void promptTanqueo(long chatId, Operador op) {
        Maquina maq = maquinaActiva(chatId, op);
        if (maq == null) { enviar(chatId, "⚠️ No tienes una máquina asignada. Usa *🚜 Máquina* para elegir una."); return; }
        estados.put(chatId, new Estado("tanqueo", null));
        enviarInline(chatId,
            "⛽ *REGISTRAR TANQUEO*\n🚜 *" + md(maq.getNombre()) + "*\n" + DIV + "\n*¿Cuántos galones y a qué precio?*\n\n" +
            "Escríbelo como te salga:\n_20 galones a 9800_\n_15 gal $9.800_\n_20 galones 196000_ (total)",
            teclado(fila(btn("✖ Cancelar", "x:cancel"))));
    }

    private void registrarTanqueo(long chatId, Operador op, TelegramUtil.Tanqueo t) {
        Maquina maq = maquinaActiva(chatId, op);
        if (maq == null) { enviar(chatId, "⚠️ No tienes una máquina asignada. Usa *🚜 Máquina* para elegir una."); return; }
        estados.remove(chatId);

        Combustible anterior = combustibleRepo.findByUsuarioIdAndMaquinaNombre(op.getUsuarioId(), maq.getNombre()).stream()
            .max(Comparator.comparing((Combustible c) -> c.getFecha() == null ? LocalDate.MIN : c.getFecha()).thenComparing(Combustible::getId)).orElse(null);

        Combustible c = new Combustible();
        c.setMaquinaNombre(maq.getNombre());
        c.setGalones(t.galones());
        c.setPrecioPorGalon(t.precio());
        c.setFecha(hoy());
        c.setHorometroAlCargar((int) Math.round(maq.getHorometroActual()));
        Combustible guardado = combustibleService.guardar(op.getUsuarioId(), c);

        StringBuilder sb = new StringBuilder("✅ *TANQUEO REGISTRADO*\n" + DIV + "\n");
        sb.append("⛽ *").append(numFmt(t.galones())).append(" gal* × ").append(TelegramUtil.dinero(t.precio())).append(" = *").append(TelegramUtil.dinero(guardado.getTotal())).append("*\n");
        sb.append("🚜 ").append(md(maq.getNombre())).append(" · horómetro *").append(numFmt(maq.getHorometroActual())).append("*\n");
        sb.append("📅 ").append(hoy().format(F_LARGA)).append("\n");
        if (anterior != null && anterior.getFecha() != null)
            sb.append("_Último tanqueo: ").append(fechaCorta(anterior.getFecha())).append(" · ").append(numFmt(anterior.getGalones())).append(" gal_\n");
        sb.append(DIV).append("\n_Se creó el gasto de combustible automáticamente._\n_Registro #").append(guardado.getId()).append("_");

        enviarInline(chatId, sb.toString(), teclado(
            fila(btn("↩️ Deshacer", "u:c:" + guardado.getId()), btn("✏️ Corregir", "x:c:" + guardado.getId())),
            fila(btn("🚜 Ver máquina", "m:ver"), btnUrl("📱 Ver en la app ↗", URL_APP))));
    }

    // ── Novedad ───────────────────────────────────────────────────

    private void promptNovedad(long chatId, Operador op) {
        Maquina maq = maquinaActiva(chatId, op);
        if (maq == null) { enviar(chatId, "⚠️ No tienes una máquina asignada. Usa *🚜 Máquina* para elegir una."); return; }
        enviarInline(chatId,
            "🛠 *REPORTAR NOVEDAD*\n🚜 *" + md(maq.getNombre()) + "* · horómetro " + numFmt(maq.getHorometroActual()) + "\n" + DIV +
            "\n*¿Qué pasó con la máquina?*\n_Tu reporte le llega a tu administrador en la sección Novedades._",
            teclado(
                fila(btn("🔴 Falla mecánica", "n:falla"), btn("💧 Fuga de aceite", "n:fuga")),
                fila(btn("⚡ Falla eléctrica", "n:elec"), btn("⛓ Oruga o llantas", "n:oruga")),
                fila(btn("🪟 Cabina o vidrios", "n:cabina"), btn("📝 Otra", "n:otra")),
                fila(btn("✖ Cancelar", "x:cancel"))));
    }

    private void guardarNovedad(long chatId, Operador op, String tipo, String descripcion) {
        Maquina maq = maquinaActiva(chatId, op);
        estados.remove(chatId);
        Novedad n = new Novedad();
        n.setUsuarioId(op.getUsuarioId());
        n.setOperadorId(op.getId());
        n.setOperadorNombre(op.getNombre());
        n.setMaquinaNombre(maq != null ? maq.getNombre() : null);
        n.setTipo(tipo);
        n.setDescripcion(descripcion);
        n.setFecha(hoy());
        n.setEstado("pendiente");
        Novedad guardada = novedadRepo.save(n);
        enviarInline(chatId,
            "✅ *NOVEDAD REPORTADA*\n" + DIV + "\n" +
            "🛠 *" + md(tipo) + "*\n" +
            (maq != null ? "🚜 " + md(maq.getNombre()) + "\n" : "") +
            "📝 " + md(descripcion) + "\n📅 " + hoy().format(F_LARGA) + "\n" + DIV +
            "\n_Quedó registrada para tu administrador._\n_Reporte #" + guardada.getId() + "_",
            teclado(fila(btn("🚜 Ver máquina", "m:ver"), btnUrl("📱 Ver en la app ↗", URL_APP))));
    }

    // ── Máquina ───────────────────────────────────────────────────

    private void handleMaquina(long chatId, Operador op) {
        Maquina maq = maquinaActiva(chatId, op);
        if (maq == null) {
            enviarInline(chatId, "🚜 *MI MÁQUINA*\n" + DIV + "\n_No tienes una máquina asignada._\nElige una para empezar.",
                teclado(fila(btn("🔁 Elegir máquina", "h:maq"))));
            return;
        }
        LocalDate hoy = hoy();
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        List<HoraTrabajada> horas = horasDe(op).stream().filter(h -> maq.getNombre().equals(h.getMaquinaNombre())).toList();
        double hoyH = sumaHoras(horas, h -> hoy.equals(h.getFecha()));
        double mes = sumaHoras(horas, h -> h.getFecha() != null && !h.getFecha().isBefore(inicioMes));

        String estado = maq.getEstado() == null ? "" : maq.getEstado().toLowerCase();
        String emoji = estado.contains("mant") ? "🟠" : (estado.contains("inac") || estado.contains("fuera") || estado.contains("dañ")) ? "🔴" : "🟢";

        StringBuilder sb = new StringBuilder("🚜 *" + md(maq.getNombre().toUpperCase()) + "*  " + emoji + " " + md(maq.getEstado() == null ? "Operativa" : maq.getEstado()) + "\n" + DIV + "\n");
        sb.append("🔢 Horómetro *").append(numFmt(maq.getHorometroActual())).append(" h*\n");
        sb.append("⏱ Hoy *").append(numFmt(hoyH)).append(" h* · Mes *").append(numFmt(mes)).append(" h*\n");

        List<Mantenimiento> mants = mantenimientoRepo.findByUsuarioIdAndMaquinaNombre(op.getUsuarioId(), maq.getNombre());
        Mantenimiento proximo = mants.stream()
            .filter(m -> "pendiente".equalsIgnoreCase(m.getEstado()) && m.getHorometro() > 0)
            .min(Comparator.comparingInt(Mantenimiento::getHorometro)).orElse(null);
        sb.append("\n*PRÓXIMO MANTENIMIENTO*\n");
        if (proximo != null) {
            double faltan = proximo.getHorometro() - maq.getHorometroActual();
            String que = proximo.getTipo() != null && !proximo.getTipo().isBlank() ? proximo.getTipo() : "Mantenimiento";
            sb.append("🔧 ").append(md(que)).append(" a las *").append(proximo.getHorometro()).append(" h*\n");
            if (faltan >= 0) {
                int previo = mants.stream().filter(m -> !"pendiente".equalsIgnoreCase(m.getEstado()) && m.getHorometro() > 0 && m.getHorometro() < proximo.getHorometro())
                    .mapToInt(Mantenimiento::getHorometro).max().orElse(-1);
                if (previo >= 0 && proximo.getHorometro() > previo)
                    sb.append(TelegramUtil.barra(maq.getHorometroActual() - previo, proximo.getHorometro() - previo, 10, '▰', '▱')).append(" ");
                sb.append("*faltan ").append(numFmt(faltan)).append(" h*\n");
            } else {
                sb.append("⚠️ *Vencido hace ").append(numFmt(-faltan)).append(" h*\n");
            }
        } else {
            sb.append("_Sin mantenimientos pendientes._\n");
        }

        Combustible ultimo = combustibleRepo.findByUsuarioIdAndMaquinaNombre(op.getUsuarioId(), maq.getNombre()).stream()
            .max(Comparator.comparing((Combustible c) -> c.getFecha() == null ? LocalDate.MIN : c.getFecha()).thenComparing(Combustible::getId)).orElse(null);
        sb.append("\n*ÚLTIMO TANQUEO*\n");
        if (ultimo != null && ultimo.getFecha() != null) sb.append("⛽ ").append(fechaCorta(ultimo.getFecha())).append(" · *").append(numFmt(ultimo.getGalones())).append(" gal*\n");
        else sb.append("_Aún sin tanqueos registrados._\n");

        long abiertas = novedadRepo.findByOperadorIdOrderByFechaDesc(op.getId()).stream()
            .filter(n -> maq.getNombre().equals(n.getMaquinaNombre()) && "pendiente".equalsIgnoreCase(n.getEstado())).count();
        sb.append("\n*NOVEDADES*\n🛠 ").append(abiertas == 0 ? "Sin novedades abiertas" : abiertas + (abiertas == 1 ? " novedad abierta" : " novedades abiertas")).append("\n");

        enviarInline(chatId, sb.toString(), teclado(
            fila(btn("⏱ Registrar horas", "h:menu"), btn("⛽ Tanqueo", "t:ver")),
            fila(btn("🛠 Reportar novedad", "n:menu"), btn("🔁 Cambiar máquina", "h:maq"))));
    }

    // ── Lo que el bot esperaba que escribieras ────────────────────

    private void handleEstado(long chatId, String text, Operador op, Estado estado) {
        switch (estado.tipo) {
            case "horas" -> {
                ParsedHoras ph = parsearHoras(text, op);
                double h = ph.horas;
                if (h <= 0) {
                    TelegramUtil.Monto m = TelegramUtil.parsearMonto(text);
                    if (m != null && m.valor() <= 24) h = m.valor();
                }
                if (h <= 0) {
                    enviarInline(chatId, "⚙ *Moventra*\n\nNo entendí las horas. Escribe solo el número, por ejemplo _7.5_",
                        teclado(fila(btn("✖ Cancelar", "x:cancel"))));
                    return;
                }
                registrarHoras(chatId, op, h, resolverMaquina(chatId, ph.maquinaMencionada, op));
            }
            case "gasto" -> {
                TelegramUtil.Monto m = TelegramUtil.parsearMonto(text);
                if (m == null) {
                    enviarInline(chatId, "⚙ *Moventra*\n\nNo entendí el monto. Escríbelo así: _85000 filtro de aceite_",
                        teclado(fila(btn("✖ Cancelar", "x:cancel"))));
                    return;
                }
                String desc = m.resto().isBlank() ? estado.dato : m.resto();
                registrarGasto(chatId, op, m.valor(), desc, estado.dato, null);
            }
            case "tanqueo" -> {
                TelegramUtil.Tanqueo t = TelegramUtil.parsearTanqueo(text);
                if (t == null) {
                    enviarInline(chatId, "⚙ *Moventra*\n\nNo entendí. Necesito *galones* y *precio*, por ejemplo:\n_20 galones a 9800_",
                        teclado(fila(btn("✖ Cancelar", "x:cancel"))));
                    return;
                }
                registrarTanqueo(chatId, op, t);
            }
            case "novedad" -> {
                String desc = text.equalsIgnoreCase("omitir") ? "Sin descripción" : text;
                if (desc.length() > 500) desc = desc.substring(0, 500);
                guardarNovedad(chatId, op, estado.dato, desc);
            }
            default -> estados.remove(chatId);
        }
    }

    // ── Foto / Documento ──────────────────────────────────────────

    private void handleFoto(long chatId, JsonNode photos, Operador op) {
        try {
            JsonNode foto = photos.get(photos.size() - 1);
            byte[] imgData = descargarArchivo(foto.path("file_id").asText());
            if (imgData == null) { enviarConMenu(chatId, "❌ No pude descargar la imagen."); return; }
            byte[] pdfData = imagenAPdf(imgData);
            leerYRegistrarFactura(chatId, op, pdfData, "application/pdf", "factura_" + System.currentTimeMillis() + ".pdf");
        } catch (Exception e) {
            System.out.println("[TG] Error foto: " + e.getMessage());
        }
    }

    private void handleDocumento(long chatId, JsonNode document, Operador op) {
        String mime = document.path("mime_type").asText("");
        if (!mime.equals("application/pdf") && !mime.startsWith("image/")) {
            enviar(chatId, "⚠️ Solo acepto fotos o PDFs como facturas."); return;
        }
        try {
            byte[] data = descargarArchivo(document.path("file_id").asText());
            if (data == null) { enviar(chatId, "❌ No pude descargar el archivo."); return; }
            String nombre = document.path("file_name").asText("factura_tg_" + System.currentTimeMillis() + ".pdf");
            leerYRegistrarFactura(chatId, op, data, mime, nombre);
        } catch (Exception e) {
            System.out.println("[TG] Error documento: " + e.getMessage());
        }
    }

    private void leerYRegistrarFactura(long chatId, Operador op, byte[] data, String mediaType, String nombre) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            adjuntarFactura(chatId, op, nombre, data);
            return;
        }
        try {
            enviar(chatId, "⚙ *Moventra*\nAnalizando factura con IA...");

            String base64 = java.util.Base64.getEncoder().encodeToString(data);
            boolean esPdf = "application/pdf".equals(mediaType);

            ObjectNode bloque = mapper.createObjectNode();
            bloque.put("type", esPdf ? "document" : "image");
            ObjectNode src = mapper.createObjectNode();
            src.put("type", "base64"); src.put("media_type", mediaType); src.put("data", base64);
            bloque.set("source", src);

            ObjectNode textBlock = mapper.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text",
                "Eres un asistente experto en facturas colombianas de taller y proveedores. " +
                "Analiza este documento y extrae los datos. " +
                "Responde ÚNICAMENTE con JSON: {\"descripcion\":\"descripción breve\",\"monto\":150000,\"categoria\":\"Repuestos\",\"fecha\":\"2024-01-15\"}. " +
                "categoria debe ser una de: Repuestos, Lubricantes, Combustible, Reparación, Otros. " +
                "monto es número entero sin puntos ni símbolos. fecha en formato YYYY-MM-DD.");

            ArrayNode content = mapper.createArrayNode(); content.add(bloque); content.add(textBlock);
            ObjectNode msg = mapper.createObjectNode(); msg.put("role", "user"); msg.set("content", content);
            ArrayNode msgs = mapper.createArrayNode(); msgs.add(msg);
            ObjectNode body = mapper.createObjectNode();
            body.put("model", "claude-haiku-4-5-20251001"); body.put("max_tokens", 300); body.set("messages", msgs);

            HttpResponse<String> res = http.send(HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("x-api-key", apiKey).header("anthropic-version", "2023-06-01").header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());

            String text = "";
            for (JsonNode b : mapper.readTree(res.body()).path("content"))
                if ("text".equals(b.path("type").asText())) { text = b.path("text").asText().trim(); break; }

            int ini = text.indexOf('{'), fin = text.lastIndexOf('}');
            if (ini < 0 || fin <= ini) throw new RuntimeException("Sin JSON");
            JsonNode json = mapper.readTree(text.substring(ini, fin + 1));

            double monto = json.path("monto").asDouble(0);
            if (monto <= 0) throw new RuntimeException("Monto no detectado");

            String descripcion = json.path("descripcion").asText("Gasto por factura");
            String categoria   = json.path("categoria").asText("Otros");
            String fechaStr    = json.path("fecha").asText(null);
            LocalDate fecha = hoy();
            if (fechaStr != null && !fechaStr.isBlank()) {
                try { fecha = LocalDate.parse(fechaStr); } catch (Exception ignored) {}
            }

            Gasto gasto = new Gasto();
            gasto.setDescripcion(descripcion);
            gasto.setMonto(monto);
            gasto.setCategoria(categoria);
            gasto.setFecha(fecha);
            Maquina maqFactura = resolverMaquina(chatId, null, op);
            if (maqFactura != null) gasto.setMaquinaNombre(maqFactura.getNombre());
            Gasto guardado = gastoService.guardar(op.getUsuarioId(), gasto);
            gastoService.guardarFactura(guardado.getId(), nombre, data);

            enviarInline(chatId,
                "✅ *FACTURA LEÍDA — GASTO REGISTRADO*\n" + DIV + "\n" +
                "💸 *" + TelegramUtil.dinero(monto) + "*\n" +
                "📝 " + md(descripcion) + "\n" +
                "🏷 " + md(categoria) + "\n" +
                "📎 Factura adjuntada\n" + DIV + "\n_Registro #" + guardado.getId() + "_",
                teclado(
                    fila(btn("↩️ Deshacer", "u:g:" + guardado.getId()), btn("✏️ Corregir", "x:g:" + guardado.getId())),
                    fila(btnUrl("📱 Ver en la app ↗", URL_APP))));

        } catch (Exception e) {
            System.out.println("[TG] IA factura falló: " + e.getMessage());
            FacturaPendiente fp = new FacturaPendiente();
            fp.data = data; fp.nombre = nombre;
            facturasPendientes.put(chatId, fp);
            enviarConMenu(chatId,
                "⚙ *Moventra*\n\nNo pude leer el monto de la factura automáticamente.\n\n" +
                "¿Cuánto fue el total? Escríbelo y lo registro con la factura adjunta:\n_Ejemplo: 85000_");
        }
    }

    private void handleMontoPendiente(long chatId, String texto, Operador op, FacturaPendiente fp) {
        TelegramUtil.Monto m = TelegramUtil.parsearMonto(texto);
        if (m == null) {
            enviarConMenu(chatId, "⚙ *Moventra*\n\nNo entendí el monto. Escríbelo así:\n_85000_ o _$85.000_");
            return;
        }
        double monto = m.valor();

        facturasPendientes.remove(chatId);

        Gasto gasto = new Gasto();
        gasto.setDescripcion("Gasto por factura");
        gasto.setMonto(monto);
        gasto.setCategoria("Otros");
        gasto.setFecha(hoy());
        Maquina maqPendiente = resolverMaquina(chatId, null, op);
        if (maqPendiente != null) gasto.setMaquinaNombre(maqPendiente.getNombre());
        Gasto guardado = gastoService.guardar(op.getUsuarioId(), gasto);
        gastoService.guardarFactura(guardado.getId(), fp.nombre, fp.data);

        enviarInline(chatId,
            "✅ *GASTO REGISTRADO CON FACTURA*\n" + DIV + "\n" +
            "💸 *" + TelegramUtil.dinero(monto) + "*\n" +
            "📎 Factura adjuntada\n" + DIV + "\n_Registro #" + guardado.getId() + " — puedes editar la descripción desde la app_",
            teclado(
                fila(btn("↩️ Deshacer", "u:g:" + guardado.getId()), btn("✏️ Corregir", "x:g:" + guardado.getId())),
                fila(btnUrl("📱 Ver en la app ↗", URL_APP))));
    }

    private void adjuntarFactura(long chatId, Operador op, String nombre, byte[] data) {
        List<Gasto> gastos = gastoRepo.findByUsuarioId(op.getUsuarioId());
        if (gastos.isEmpty()) {
            enviarConMenu(chatId, "⚠️ Registra primero un gasto y luego envía la factura."); return;
        }
        Gasto ultimo = gastos.stream().max(Comparator.comparingLong(Gasto::getId)).orElseThrow();
        gastoService.guardarFactura(ultimo.getId(), nombre, data);
        enviarConMenu(chatId, "✅ *Factura adjuntada* al gasto *#" + ultimo.getId() + "*\n📝 " + md(ultimo.getDescripcion() != null ? ultimo.getDescripcion() : "Sin descripción"));
    }

    // ── Parsing ───────────────────────────────────────────────────

    private boolean esGasto(String lower) {
        return lower.startsWith("gast") || lower.startsWith("compr") || lower.startsWith("pagu")
            || lower.startsWith("factur") || lower.contains(" gasto ") || lower.contains("taller")
            || (lower.contains("aceite") && lower.matches(".*\\d.*"));
    }

    private ParsedHoras parsearHoras(String mensaje, Operador op) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                String lista = maquinaRepo.findByUsuarioId(op.getUsuarioId()).stream().map(Maquina::getNombre).collect(Collectors.joining(", "));
                String resp = llamarClaude(apiKey,
                    "Operador de maquinaria pesada envió: '" + mensaje + "'. Máquinas disponibles: " + lista + ". " +
                    "Extrae horas trabajadas y máquina. JSON solo: {\"horas\": 8.0, \"maquina\": \"nombre exacto o null\"}. " +
                    "Si no hay horas claras pon 0.");
                JsonNode json = mapper.readTree(resp);
                ParsedHoras p = new ParsedHoras();
                p.horas = json.path("horas").asDouble(0);
                JsonNode m = json.path("maquina");
                p.maquinaMencionada = (m.isNull() || m.isMissingNode()) ? null : m.asText(null);
                return p;
            } catch (Exception e) { System.out.println("[TG] IA horas falló: " + e.getMessage()); }
        }
        ParsedHoras p = new ParsedHoras();
        Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*h(?:oras?)?", Pattern.CASE_INSENSITIVE).matcher(mensaje);
        if (m.find()) p.horas = Double.parseDouble(m.group(1).replace(',', '.'));
        return p;
    }

    private ParsedGasto parsearGasto(String mensaje, Operador op) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                String lista = maquinaRepo.findByUsuarioId(op.getUsuarioId()).stream().map(Maquina::getNombre).collect(Collectors.joining(", "));
                String resp = llamarClaude(apiKey,
                    "Operador reportó gasto: '" + mensaje + "'. Máquinas: " + lista + ". " +
                    "Extrae: monto (número sin $), descripción breve, categoría (Combustible/Mantenimiento/Repuestos/Mano de obra/Transporte/Otro), máquina si menciona. " +
                    "JSON solo: {\"monto\": 85000, \"descripcion\": \"Filtro aceite\", \"categoria\": \"Repuestos\", \"maquina\": \"nombre o null\"}");
                JsonNode json = mapper.readTree(resp);
                ParsedGasto pg = new ParsedGasto();
                pg.monto = json.path("monto").asDouble(0);
                pg.descripcion = json.path("descripcion").asText("Gasto por Telegram");
                pg.categoria   = json.path("categoria").asText("Otro");
                JsonNode maq   = json.path("maquina");
                pg.maquinaNombre = (maq.isNull() || maq.isMissingNode()) ? null : maq.asText(null);
                return pg;
            } catch (Exception e) { System.out.println("[TG] IA gasto falló: " + e.getMessage()); }
        }
        // Fallback regex
        ParsedGasto pg = new ParsedGasto();
        TelegramUtil.Monto monto = TelegramUtil.parsearMonto(mensaje);
        if (monto != null) {
            pg.monto = monto.valor();
            if (!monto.resto().isBlank()) pg.descripcion = monto.resto();
        }
        String lower = mensaje.toLowerCase();
        if      (lower.contains("combustible") || lower.contains("diesel") || lower.contains("acpm")) pg.categoria = "Combustible";
        else if (lower.contains("aceite") || lower.contains("filtro") || lower.contains("mantenimiento")) pg.categoria = "Mantenimiento";
        else if (lower.contains("repuesto") || lower.contains("pieza")) pg.categoria = "Repuestos";
        else if (lower.contains("taller")   || lower.contains("mano de obra")) pg.categoria = "Mano de obra";
        return pg;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private String llamarClaude(String apiKey, String prompt) throws Exception {
        ObjectNode textBlock = mapper.createObjectNode(); textBlock.put("type", "text"); textBlock.put("text", prompt);
        ArrayNode content = mapper.createArrayNode(); content.add(textBlock);
        ObjectNode message = mapper.createObjectNode(); message.put("role", "user"); message.set("content", content);
        ArrayNode messages = mapper.createArrayNode(); messages.add(message);
        ObjectNode body = mapper.createObjectNode();
        body.put("model", "claude-haiku-4-5-20251001"); body.put("max_tokens", 150); body.set("messages", messages);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("x-api-key", apiKey).header("anthropic-version", "2023-06-01").header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(res.body());
        String text = "";
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) { text = block.path("text").asText().trim(); break; }
        }
        int ini = text.indexOf('{'), fin = text.lastIndexOf('}');
        return (ini >= 0 && fin > ini) ? text.substring(ini, fin + 1) : "{}";
    }

    // La máquina que el operador menciona; si no, la que eligió con "Cambiar máquina"; si no, la que tiene asignada
    private Maquina resolverMaquina(long chatId, String mencionada, Operador op) {
        List<Maquina> todas = maquinaRepo.findByUsuarioId(op.getUsuarioId());
        if (mencionada != null && !mencionada.isBlank()) {
            String q = mencionada.toLowerCase();
            Maquina enc = todas.stream()
                .filter(m -> m.getNombre().toLowerCase().contains(q) || q.contains(m.getNombre().toLowerCase()))
                .findFirst().orElse(null);
            if (enc != null) return enc;
        }
        Long elegida = maquinaElegida.get(chatId);
        if (elegida != null) {
            Maquina enc = todas.stream().filter(m -> elegida.equals(m.getId())).findFirst().orElse(null);
            if (enc != null) return enc;
        }
        List<Maquina> asignadas = maquinaRepo.findByUsuarioIdAndOperadorNombre(op.getUsuarioId(), op.getNombre());
        return asignadas.isEmpty() ? null : asignadas.get(0);
    }

    private Maquina maquinaActiva(long chatId, Operador op) { return resolverMaquina(chatId, null, op); }

    private byte[] imagenAPdf(byte[] imgBytes) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(
                org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject img =
                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(doc, imgBytes, "factura");
            float pw = page.getMediaBox().getWidth();
            float ph = page.getMediaBox().getHeight();
            float margin = 20f;
            float scale = Math.min((pw - margin * 2) / img.getWidth(), (ph - margin * 2) / img.getHeight());
            float dw = img.getWidth() * scale;
            float dh = img.getHeight() * scale;
            float x = (pw - dw) / 2f;
            float y = (ph - dh) / 2f;
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                    new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.drawImage(img, x, y, dw, dh);
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] descargarArchivo(String fileId) {
        try {
            HttpResponse<String> meta = http.send(
                HttpRequest.newBuilder().uri(URI.create("https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            String filePath = mapper.readTree(meta.body()).path("result").path("file_path").asText();
            return http.send(
                HttpRequest.newBuilder().uri(URI.create("https://api.telegram.org/file/bot" + botToken + "/" + filePath)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body();
        } catch (Exception e) { System.out.println("[TG] Error descarga: " + e.getMessage()); return null; }
    }

    // ── Envío ─────────────────────────────────────────────────────

    private static String md(String s) { return TelegramUtil.md(s); }

    private ObjectNode btn(String texto, String data) {
        ObjectNode b = mapper.createObjectNode(); b.put("text", texto); b.put("callback_data", data); return b;
    }

    private ObjectNode btnUrl(String texto, String url) {
        ObjectNode b = mapper.createObjectNode(); b.put("text", texto); b.put("url", url); return b;
    }

    private ArrayNode fila(ObjectNode... botones) {
        ArrayNode f = mapper.createArrayNode();
        for (ObjectNode b : botones) f.add(b);
        return f;
    }

    private ArrayNode teclado(ArrayNode... filas) {
        ArrayNode k = mapper.createArrayNode();
        for (ArrayNode f : filas) k.add(f);
        return k;
    }

    private void postTelegram(String metodo, ObjectNode body) {
        try {
            http.send(HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + botToken + "/" + metodo))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { System.out.println("[TG] Error " + metodo + ": " + e.getMessage()); }
    }

    private void responderCallback(String callbackId) {
        if (botToken == null || botToken.isBlank() || callbackId.isBlank()) return;
        ObjectNode body = mapper.createObjectNode();
        body.put("callback_query_id", callbackId);
        postTelegram("answerCallbackQuery", body);
    }

    // Mensaje con botones dentro del mensaje; el teclado fijo de abajo sigue visible
    private void enviarInline(long chatId, String texto, ArrayNode filas) {
        if (botToken == null || botToken.isBlank()) return;
        ObjectNode markup = mapper.createObjectNode();
        markup.set("inline_keyboard", filas);
        ObjectNode body = mapper.createObjectNode();
        body.put("chat_id", chatId); body.put("text", texto); body.put("parse_mode", "Markdown");
        body.put("disable_web_page_preview", true);
        body.set("reply_markup", markup);
        postTelegram("sendMessage", body);
    }

    // Mensaje que además (re)pone el teclado fijo de 3 x 3
    private void enviarConMenu(long chatId, String texto) {
        if (botToken == null || botToken.isBlank()) return;
        tecladoAlDia.add(chatId);
        ArrayNode keyboard = teclado(
            filaTexto(BTN_HORAS, BTN_GASTO, BTN_FACTURA),
            filaTexto(BTN_CORTES, BTN_SALDO, BTN_RESUMEN),
            filaTexto(BTN_TANQUEO, BTN_NOVEDAD, BTN_MAQUINA));
        ObjectNode markup = mapper.createObjectNode();
        markup.set("keyboard", keyboard);
        markup.put("resize_keyboard", true);
        markup.put("one_time_keyboard", false);
        markup.put("persistent", true);

        ObjectNode body = mapper.createObjectNode();
        body.put("chat_id", chatId); body.put("text", texto); body.put("parse_mode", "Markdown");
        body.set("reply_markup", markup);
        postTelegram("sendMessage", body);
    }

    private ArrayNode filaTexto(String... textos) {
        ArrayNode f = mapper.createArrayNode();
        for (String t : textos) { ObjectNode b = mapper.createObjectNode(); b.put("text", t); f.add(b); }
        return f;
    }

    private void enviar(long chatId, String texto) {
        if (botToken == null || botToken.isBlank()) return;
        ObjectNode body = mapper.createObjectNode();
        body.put("chat_id", chatId); body.put("text", texto); body.put("parse_mode", "Markdown");
        postTelegram("sendMessage", body);
    }

    private String numFmt(double v) { return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(Math.round(v * 10) / 10.0); }

    static class ParsedHoras { double horas = 0; String maquinaMencionada = null; }
    static class ParsedGasto { double monto = 0; String descripcion = "Gasto por Telegram"; String categoria = "Otro"; String maquinaNombre = null; }
}
