package com.maquicontrol.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maquicontrol.backend.model.*;
import com.maquicontrol.backend.repository.*;
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
    @Autowired private HoraTrabajadaService horaService;
    @Autowired private GastoService gastoService;
    @Autowired private IngresoService ingresoService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private static final String BTN_HORAS   = "⏱ Registrar horas";
    private static final String BTN_GASTO   = "💸 Registrar gasto";
    private static final String BTN_RESUMEN = "📊 Mi resumen";
    private static final String BTN_AYUDA   = "ℹ️ Ayuda";

    // Facturas pendientes cuando la IA no pudo leer el monto
    // clave: chatId → {data, nombre}
    private static class FacturaPendiente { byte[] data; String nombre; }
    private final java.util.concurrent.ConcurrentHashMap<Long, FacturaPendiente> facturasPendientes = new java.util.concurrent.ConcurrentHashMap<>();

    // ── Webhook principal ──────────────────────────────────────────

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload) {
        try {
            JsonNode update = mapper.readTree(payload);
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
                enviar(chatId, "⚙ *MaquiControl*\n\nNo estás vinculado.\nPide el código a tu administrador y envía:\n*/start CODIGO*");
                return ResponseEntity.ok("ok");
            }

            // Foto o documento → factura
            if (!photos.isMissingNode() && photos.isArray() && photos.size() > 0) {
                handleFoto(chatId, photos, operador);
            } else if (!document.isMissingNode() && !document.isNull()) {
                handleDocumento(chatId, document, operador);
            } else {
                String lower = text.toLowerCase();

                // Respuesta al monto pendiente cuando la IA no pudo leer la factura
                FacturaPendiente pendiente = facturasPendientes.get(chatId);
                if (pendiente != null && !text.equals(BTN_HORAS) && !text.equals(BTN_GASTO)
                        && !text.equals(BTN_RESUMEN) && !text.equals(BTN_AYUDA)
                        && !lower.startsWith("/")) {
                    handleMontoPendiente(chatId, text, operador, pendiente);
                } else if (text.equals(BTN_HORAS)) {
                    enviarConMenu(chatId, "⚙ *MaquiControl*\n\nEnvíame las horas trabajadas. Ejemplos:\n• _8 horas_\n• _9h CAT 320_\n• _7.5 horas excavadora_\n\nSolo escribe y lo registro automáticamente.");
                } else if (text.equals(BTN_GASTO)) {
                    enviarConMenu(chatId, "⚙ *MaquiControl*\n\nEnvíame el gasto. Ejemplos:\n• _gasté $85.000 en filtro_\n• _gasto 150000 aceite CAT_\n• _compré repuesto 75000_\n\nO envía directamente la foto o PDF de la factura.");
                } else if (text.equals(BTN_RESUMEN) || lower.startsWith("/resumen") || lower.equals("resumen") || lower.equals("mis horas")) {
                    handleResumen(chatId, operador);
                } else if (text.equals(BTN_AYUDA) || lower.equals("/menu") || lower.equals("menu") || lower.equals("hola") || lower.equals("ayuda") || lower.equals("/ayuda")) {
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

    // ── /start ────────────────────────────────────────────────────

    private void handleStart(long chatId, String text, Operador existente) {
        if (existente != null) {
            enviarConMenu(chatId, "✅ Ya estás vinculado como *" + existente.getNombre() + "*.\n¿Qué deseas hacer?");
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            enviar(chatId, "⚙ *MaquiControl*\n\nPara vincular tu cuenta envía:\n*/start CODIGO*\n\nPide el código a tu administrador.");
            return;
        }
        String code = parts[1].trim().toUpperCase();
        operadorRepo.findByTelegramLinkCode(code).ifPresentOrElse(op -> {
            op.setTelegramChatId(chatId);
            op.setTelegramLinkCode(null);
            operadorRepo.save(op);
            enviarConMenu(chatId, "✅ ¡Hola *" + op.getNombre() + "*!\nTu cuenta está vinculada a MaquiControl.\n\nUsa los botones para registrar horas y gastos.");
        }, () -> enviar(chatId, "❌ Código inválido o ya usado.\nPide un nuevo código a tu administrador."));
    }

    // ── Menú ──────────────────────────────────────────────────────

    private void handleMenu(long chatId, Operador op) {
        enviarConMenu(chatId,
            "⚙ *MaquiControl*\nHola *" + op.getNombre() + "*, ¿qué deseas hacer?\n\n" +
            "📎 También puedes enviar una foto o PDF de factura en cualquier momento.");
    }

    // ── Resumen ───────────────────────────────────────────────────

    private void handleResumen(long chatId, Operador op) {
        LocalDate inicioSemana = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate inicioMes    = LocalDate.now().withDayOfMonth(1);
        List<HoraTrabajada> horas = horaRepo.findByOperadorIdOrNombre(op.getId(), op.getNombre());
        double semana = horas.stream().filter(h -> h.getFecha() != null && !h.getFecha().isBefore(inicioSemana)).mapToDouble(HoraTrabajada::getHoras).sum();
        double mes    = horas.stream().filter(h -> h.getFecha() != null && !h.getFecha().isBefore(inicioMes)).mapToDouble(HoraTrabajada::getHoras).sum();
        enviarConMenu(chatId,
            "⚙ *MaquiControl* — Tu resumen\n─────────────────\n" +
            "📅 Esta semana: *" + numFmt(semana) + "h*\n" +
            "📆 Este mes: *"    + numFmt(mes)    + "h*\n" +
            "─────────────────\n_¡Buen trabajo " + op.getNombre().split(" ")[0] + "!_");
    }

    // ── Horas ─────────────────────────────────────────────────────

    private void handleHoras(long chatId, String text, Operador op) {
        ParsedHoras ph = parsearHoras(text, op);
        if (ph.horas <= 0) {
            enviarConMenu(chatId,
                "⚙ *MaquiControl*\n\nNo entendí las horas. Ejemplos:\n" +
                "• _8 horas_\n• _9h CAT 320_\n• _7.5 horas excavadora_");
            return;
        }

        Maquina maq = resolverMaquina(ph.maquinaMencionada, op);

        HoraTrabajada hora = new HoraTrabajada();
        hora.setOperadorId(op.getId());
        hora.setOperadorNombre(op.getNombre());
        hora.setHoras(ph.horas);
        hora.setFecha(LocalDate.now());
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
        horaService.guardar(op.getUsuarioId(), hora);

        if (maq != null && maq.getValorHoraMaquina() > 0) {
            Ingreso ing = new Ingreso();
            ing.setMaquinaNombre(maq.getNombre());
            ing.setTipoTrabajo("Horas");
            ing.setCantidad(ph.horas);
            ing.setValorUnitario(maq.getValorHoraMaquina());
            ing.setFecha(LocalDate.now());
            ing.setDescripcion(numFmt(ph.horas) + "h — " + maq.getNombre() + " (Telegram)");
            ingresoService.guardar(op.getUsuarioId(), ing);
        }

        LocalDate inicioSemana = LocalDate.now().with(DayOfWeek.MONDAY);
        double totalSemana = horaRepo.findByOperadorIdOrNombre(op.getId(), op.getNombre()).stream()
            .filter(h -> h.getFecha() != null && !h.getFecha().isBefore(inicioSemana))
            .mapToDouble(HoraTrabajada::getHoras).sum();

        String fechaTxt = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("es", "CO")));
        String maqTxt   = maq != null ? maq.getNombre() : "Sin máquina asignada";

        enviarConMenu(chatId,
            "⚙ *MaquiControl*\n─────────────────\n" +
            "✅ *" + numFmt(ph.horas) + "h registradas*\n" +
            "🚜 " + maqTxt + "\n" +
            "📅 " + fechaTxt + "\n" +
            "📊 Acumulado semana: *" + numFmt(totalSemana) + "h*\n" +
            "─────────────────\n_Puedes enviar foto de factura si tienes algún gasto_");
    }

    // ── Gasto ─────────────────────────────────────────────────────

    private void handleGasto(long chatId, String text, Operador op) {
        ParsedGasto pg = parsearGasto(text, op);
        if (pg.monto <= 0) {
            enviarConMenu(chatId,
                "⚙ *MaquiControl*\n\nNo entendí el monto. Ejemplos:\n" +
                "• _gasté $85.000 en filtro_\n• _gasto 150000 aceite CAT_\n• _compré repuesto 75000_");
            return;
        }

        Gasto gasto = new Gasto();
        gasto.setDescripcion(pg.descripcion);
        gasto.setMonto(pg.monto);
        gasto.setCategoria(pg.categoria);
        gasto.setFecha(LocalDate.now());
        Maquina maqGasto = resolverMaquina(pg.maquinaNombre, op);
        if (maqGasto != null) gasto.setMaquinaNombre(maqGasto.getNombre());
        Gasto guardado = gastoService.guardar(op.getUsuarioId(), gasto);

        String montoFmt = "$" + String.format("%,.0f", pg.monto).replace(",", ".");
        String maqLinea = gasto.getMaquinaNombre() != null ? "\n🚜 " + gasto.getMaquinaNombre() : "";

        enviarConMenu(chatId,
            "⚙ *MaquiControl*\n─────────────────\n" +
            "✅ *Gasto registrado*\n" +
            "💸 *" + montoFmt + "*\n" +
            "📝 " + pg.descripcion + maqLinea + "\n" +
            "🏷 " + pg.categoria + "\n" +
            "─────────────────\n_Puedes enviar la foto de la factura ahora_\n_ID: #" + guardado.getId() + "_");
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
            enviar(chatId, "⚙ *MaquiControl*\nAnalizando factura con IA...");

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
            LocalDate fecha = LocalDate.now();
            if (fechaStr != null && !fechaStr.isBlank()) {
                try { fecha = LocalDate.parse(fechaStr); } catch (Exception ignored) {}
            }

            Gasto gasto = new Gasto();
            gasto.setDescripcion(descripcion);
            gasto.setMonto(monto);
            gasto.setCategoria(categoria);
            gasto.setFecha(fecha);
            Maquina maqFactura = resolverMaquina(null, op);
            if (maqFactura != null) gasto.setMaquinaNombre(maqFactura.getNombre());
            Gasto guardado = gastoService.guardar(op.getUsuarioId(), gasto);
            gastoService.guardarFactura(guardado.getId(), nombre, data);

            String montoFmt = "$" + String.format("%,.0f", monto).replace(",", ".");
            enviarConMenu(chatId,
                "⚙ *MaquiControl*\n─────────────────\n" +
                "✅ *Factura leída — gasto registrado*\n" +
                "💸 *" + montoFmt + "*\n" +
                "📝 " + descripcion + "\n" +
                "🏷 " + categoria + "\n" +
                "📎 Factura adjuntada\n" +
                "─────────────────\n_ID: #" + guardado.getId() + "_");

        } catch (Exception e) {
            System.out.println("[TG] IA factura falló: " + e.getMessage());
            FacturaPendiente fp = new FacturaPendiente();
            fp.data = data; fp.nombre = nombre;
            facturasPendientes.put(chatId, fp);
            enviarConMenu(chatId,
                "⚙ *MaquiControl*\n\nNo pude leer el monto de la factura automáticamente.\n\n" +
                "¿Cuánto fue el total? Escríbelo y lo registro con la factura adjunta:\n_Ejemplo: 85000_");
        }
    }

    private void handleMontoPendiente(long chatId, String texto, Operador op, FacturaPendiente fp) {
        // Extraer monto del texto
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\\$?\\s*(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{1,2})?|\\d+)")
            .matcher(texto);
        if (!m.find()) {
            enviarConMenu(chatId, "⚙ *MaquiControl*\n\nNo entendí el monto. Escríbelo así:\n_85000_ o _$85.000_");
            return;
        }
        double monto;
        try { monto = Double.parseDouble(m.group(1).replaceAll("[.,](?=\\d{3})", "").replace(',', '.')); }
        catch (Exception e) { enviarConMenu(chatId, "⚙ *MaquiControl*\n\nNo entendí el monto. Intenta de nuevo."); return; }

        facturasPendientes.remove(chatId);

        Gasto gasto = new Gasto();
        gasto.setDescripcion("Gasto por factura");
        gasto.setMonto(monto);
        gasto.setCategoria("Otros");
        gasto.setFecha(java.time.LocalDate.now());
        Maquina maqPendiente = resolverMaquina(null, op);
        if (maqPendiente != null) gasto.setMaquinaNombre(maqPendiente.getNombre());
        Gasto guardado = gastoService.guardar(op.getUsuarioId(), gasto);
        gastoService.guardarFactura(guardado.getId(), fp.nombre, fp.data);

        String montoFmt = "$" + String.format("%,.0f", monto).replace(",", ".");
        enviarConMenu(chatId,
            "⚙ *MaquiControl*\n─────────────────\n" +
            "✅ *Gasto registrado con factura*\n" +
            "💸 *" + montoFmt + "*\n" +
            "📎 Factura adjuntada\n" +
            "─────────────────\n_ID: #" + guardado.getId() + " — puedes editar la descripción desde la app_");
    }

    private void adjuntarFactura(long chatId, Operador op, String nombre, byte[] data) {
        List<Gasto> gastos = gastoRepo.findByUsuarioId(op.getUsuarioId());
        if (gastos.isEmpty()) {
            enviarConMenu(chatId, "⚠️ Registra primero un gasto y luego envía la factura."); return;
        }
        Gasto ultimo = gastos.stream().max(Comparator.comparingLong(Gasto::getId)).orElseThrow();
        gastoService.guardarFactura(ultimo.getId(), nombre, data);
        enviarConMenu(chatId, "✅ *Factura adjuntada* al gasto *#" + ultimo.getId() + "*\n📝 " + (ultimo.getDescripcion() != null ? ultimo.getDescripcion() : "Sin descripción"));
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
        Matcher m = Pattern.compile("\\$?\\s*(\\d{1,3}(?:[.,]\\d{3})*(?:[.,]\\d{1,2})?|\\d+)").matcher(mensaje);
        if (m.find()) { try { pg.monto = Double.parseDouble(m.group(1).replaceAll("[.,](?=\\d{3})", "").replace(',', '.')); } catch (Exception ignored) {} }
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

    private Maquina resolverMaquina(String mencionada, Operador op) {
        List<Maquina> todas = maquinaRepo.findByUsuarioId(op.getUsuarioId());
        if (mencionada != null && !mencionada.isBlank()) {
            String q = mencionada.toLowerCase();
            Maquina enc = todas.stream()
                .filter(m -> m.getNombre().toLowerCase().contains(q) || q.contains(m.getNombre().toLowerCase()))
                .findFirst().orElse(null);
            if (enc != null) return enc;
        }
        List<Maquina> asignadas = maquinaRepo.findByUsuarioIdAndOperadorNombre(op.getUsuarioId(), op.getNombre());
        return asignadas.isEmpty() ? null : asignadas.get(0);
    }

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

    private void enviarConMenu(long chatId, String texto) {
        if (botToken == null || botToken.isBlank()) return;
        try {
            ObjectNode b1 = mapper.createObjectNode(); b1.put("text", BTN_HORAS);
            ObjectNode b2 = mapper.createObjectNode(); b2.put("text", BTN_GASTO);
            ObjectNode b3 = mapper.createObjectNode(); b3.put("text", BTN_RESUMEN);
            ObjectNode b4 = mapper.createObjectNode(); b4.put("text", BTN_AYUDA);
            ArrayNode row1 = mapper.createArrayNode(); row1.add(b1); row1.add(b2);
            ArrayNode row2 = mapper.createArrayNode(); row2.add(b3); row2.add(b4);
            ArrayNode keyboard = mapper.createArrayNode(); keyboard.add(row1); keyboard.add(row2);
            ObjectNode markup = mapper.createObjectNode();
            markup.set("keyboard", keyboard);
            markup.put("resize_keyboard", true);
            markup.put("one_time_keyboard", false);
            markup.put("persistent", true);

            ObjectNode body = mapper.createObjectNode();
            body.put("chat_id", chatId); body.put("text", texto); body.put("parse_mode", "Markdown");
            body.set("reply_markup", markup);
            http.send(HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + botToken + "/sendMessage"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { System.out.println("[TG] Error envío con menú: " + e.getMessage()); }
    }

    private void enviar(long chatId, String texto) {
        if (botToken == null || botToken.isBlank()) return;
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("chat_id", chatId); body.put("text", texto); body.put("parse_mode", "Markdown");
            http.send(HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + botToken + "/sendMessage"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { System.out.println("[TG] Error envío: " + e.getMessage()); }
    }

    private String numFmt(double v) { return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v); }

    static class ParsedHoras { double horas = 0; String maquinaMencionada = null; }
    static class ParsedGasto { double monto = 0; String descripcion = "Gasto por Telegram"; String categoria = "Otro"; String maquinaNombre = null; }
}
