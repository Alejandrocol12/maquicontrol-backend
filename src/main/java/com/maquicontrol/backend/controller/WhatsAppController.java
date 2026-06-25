package com.maquicontrol.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maquicontrol.backend.model.HoraTrabajada;
import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.model.Operador;
import com.maquicontrol.backend.model.Ingreso;
import com.maquicontrol.backend.repository.HoraTrabajadaRepository;
import com.maquicontrol.backend.repository.IngresoRepository;
import com.maquicontrol.backend.repository.MaquinaRepository;
import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.service.HoraTrabajadaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
public class WhatsAppController {

    @Autowired private OperadorRepository operadorRepo;
    @Autowired private MaquinaRepository maquinaRepo;
    @Autowired private HoraTrabajadaRepository horaRepo;
    @Autowired private IngresoRepository ingresoRepo;
    @Autowired private HoraTrabajadaService horaService;

    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping(value = "/api/whatsapp/entrada", produces = "text/xml;charset=UTF-8")
    public ResponseEntity<String> recibirMensaje(
        @RequestParam(value = "From", required = false, defaultValue = "") String from,
        @RequestParam(value = "Body", required = false, defaultValue = "") String body
    ) {
        System.out.println("[WA] From=" + from + " Body=" + body);

        // 1. Normalizar número: quitar "whatsapp:", "+57", espacios y guiones
        String tel = from.replace("whatsapp:", "").replaceAll("[\\s\\-()]+", "");
        // Guardar versión sin código de país para comparar con formatos colombianos
        String telCorto = tel.startsWith("+57") ? tel.substring(3)
                        : tel.startsWith("57") && tel.length() > 10 ? tel.substring(2)
                        : tel;

        // 2. Buscar operador por teléfono (soporta distintos formatos guardados)
        Operador operador = encontrarOperadorPorTelefono(tel, telCorto);

        if (operador == null) {
            return twiml("No estás registrado en MaquiControl. Pide a tu administrador que agregue tu número *" + tel + "* en tu perfil de operador.");
        }

        // 3. Parsear horas y máquina del mensaje
        ParsedHoras parsed = parsearMensaje(body.trim(), operador);

        if (parsed.horas <= 0) {
            return twiml(
                "Hola *" + operador.getNombre() + "* 👋\n" +
                "No entendí la cantidad de horas. Escríbeme así:\n" +
                "• *8 horas*\n• *9h CAT 320*\n• *7.5 horas excavadora*"
            );
        }

        // 4. Buscar máquina
        Maquina maquina = resolverMaquina(parsed.maquinaMencionada, operador);

        // 5. Obtener tarifas directamente de la máquina asignada
        double valorHoraOperador = (maquina != null && maquina.getValorHoraOperador() > 0)
            ? maquina.getValorHoraOperador()
            : horaRepo.findByOperadorIdOrNombre(operador.getId(), operador.getNombre())
                .stream().filter(h -> h.getValorHora() > 0)
                .mapToDouble(HoraTrabajada::getValorHora).max().orElse(0.0);

        double valorHoraMaquina = (maquina != null) ? maquina.getValorHoraMaquina() : 0.0;

        // 6. Guardar horas usando el servicio (maneja faena y horómetro automáticamente)
        HoraTrabajada hora = new HoraTrabajada();
        hora.setOperadorId(operador.getId());
        hora.setOperadorNombre(operador.getNombre());
        hora.setHoras(parsed.horas);
        hora.setFecha(LocalDate.now());
        hora.setValorHora(valorHoraOperador);
        if (maquina != null) {
            hora.setMaquinaId(maquina.getId());
            hora.setMaquinaNombre(maquina.getNombre());
        }
        HoraTrabajada horaGuardada = horaService.guardar(operador.getUsuarioId(), hora);
        System.out.println("[WA] Horas guardadas: " + parsed.horas + "h operador=" + operador.getNombre()
            + " valorHoraOp=" + valorHoraOperador + " valorHoraMaq=" + valorHoraMaquina);

        // 7. Crear Ingreso si la máquina tiene tarifa configurada
        if (maquina != null && valorHoraMaquina > 0) {
            double total = parsed.horas * valorHoraMaquina;
            Ingreso ingreso = new Ingreso();
            ingreso.setUsuarioId(operador.getUsuarioId());
            ingreso.setMaquinaNombre(maquina.getNombre());
            ingreso.setTipoTrabajo("Horas");
            ingreso.setCantidad(parsed.horas);
            ingreso.setValorUnitario(valorHoraMaquina);
            ingreso.setTotal(total);
            ingreso.setFecha(LocalDate.now());
            ingreso.setDescripcion("Horas – " + maquina.getNombre() + " (WhatsApp)");
            if (horaGuardada.getFaenaId() != null) ingreso.setFaenaId(horaGuardada.getFaenaId());
            ingresoRepo.save(ingreso);
            System.out.println("[WA] Ingreso creado: $" + total);
        }

        // 7. Calcular total de la semana
        LocalDate inicioSemana = LocalDate.now().with(DayOfWeek.MONDAY);
        double totalSemana = horaRepo
            .findByOperadorIdOrNombre(operador.getId(), operador.getNombre())
            .stream()
            .filter(h -> h.getFecha() != null && !h.getFecha().isBefore(inicioSemana))
            .mapToDouble(HoraTrabajada::getHoras)
            .sum();

        // 9. Construir respuesta
        String horasTxt = parsed.horas == Math.floor(parsed.horas)
            ? String.valueOf((int) parsed.horas)
            : String.valueOf(parsed.horas);
        String maquinaTxt = maquina != null ? " en *" + maquina.getNombre() + "*" : "";
        String totalTxt = totalSemana == Math.floor(totalSemana)
            ? String.valueOf((int) totalSemana)
            : String.valueOf(totalSemana);

        String respuesta = String.format(
            "✅ *%sh registradas* para hoy%s.\n📊 Acumulado esta semana: *%sh*",
            horasTxt, maquinaTxt, totalTxt
        );

        return twiml(respuesta.toString());
    }

    // Busca el operador probando distintos formatos de número
    private Operador encontrarOperadorPorTelefono(String telCompleto, String telCorto) {
        // Buscar por número corto (ej: 3001234567)
        List<Operador> byCorto = operadorRepo.findByTelefono(telCorto);
        if (!byCorto.isEmpty()) return byCorto.get(0);

        // Buscar por número completo con código (+573001234567)
        List<Operador> byCompleto = operadorRepo.findByTelefono(telCompleto);
        if (!byCompleto.isEmpty()) return byCompleto.get(0);

        // Buscar normalizando todos los operadores en memoria
        return operadorRepo.findAll().stream()
            .filter(o -> o.getTelefono() != null)
            .filter(o -> {
                String t = o.getTelefono().replaceAll("[\\s\\-()]+", "");
                String tc = t.startsWith("+57") ? t.substring(3)
                          : t.startsWith("57") && t.length() > 10 ? t.substring(2)
                          : t;
                return tc.equals(telCorto) || t.equals(telCompleto);
            })
            .findFirst().orElse(null);
    }

    // Resuelve qué máquina usar: mencionada en mensaje > asignada al operador > null
    private Maquina resolverMaquina(String mencionada, Operador operador) {
        List<Maquina> todas = maquinaRepo.findByUsuarioId(operador.getUsuarioId());

        if (mencionada != null && !mencionada.isBlank()) {
            String q = mencionada.toLowerCase();
            Maquina encontrada = todas.stream()
                .filter(m -> m.getNombre().toLowerCase().contains(q) || q.contains(m.getNombre().toLowerCase()))
                .findFirst().orElse(null);
            if (encontrada != null) return encontrada;
        }

        // Fallback: máquina actualmente asignada al operador
        List<Maquina> asignadas = maquinaRepo.findByUsuarioIdAndOperadorNombre(
            operador.getUsuarioId(), operador.getNombre()
        );
        return asignadas.isEmpty() ? null : asignadas.get(0);
    }

    // Intenta parsear con Claude IA; si falla, usa regex
    private ParsedHoras parsearMensaje(String mensaje, Operador operador) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                return parsearConIA(mensaje, operador, apiKey);
            } catch (Exception e) {
                System.out.println("[WA] IA falló, usando regex: " + e.getMessage());
            }
        }
        return parsearConRegex(mensaje);
    }

    private ParsedHoras parsearConIA(String mensaje, Operador operador, String apiKey) throws Exception {
        List<Maquina> maquinas = maquinaRepo.findByUsuarioId(operador.getUsuarioId());
        String listaMaquinas = maquinas.stream().map(Maquina::getNombre).collect(Collectors.joining(", "));

        String prompt = "El operador de maquinaria pesada envió este mensaje por WhatsApp: '" + mensaje + "'. " +
            "Máquinas registradas en el sistema: " + listaMaquinas + ". " +
            "Extrae las horas trabajadas y la máquina mencionada. " +
            "Responde SOLO con JSON válido, sin texto adicional: " +
            "{\"horas\": 8.0, \"maquina\": \"nombre exacto de la lista o null si no menciona\"}. " +
            "horas debe ser número decimal positivo. Si no hay horas claras, pon 0.";

        ObjectNode textBlock = mapper.createObjectNode();
        textBlock.put("type", "text");
        textBlock.put("text", prompt);

        ArrayNode contentArray = mapper.createArrayNode();
        contentArray.add(textBlock);

        ObjectNode message = mapper.createObjectNode();
        message.put("role", "user");
        message.set("content", contentArray);

        ArrayNode messages = mapper.createArrayNode();
        messages.add(message);

        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", "claude-haiku-4-5-20251001");
        requestBody.put("max_tokens", 100);
        requestBody.set("messages", messages);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.anthropic.com/v1/messages"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());

        String text = "";
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text = block.path("text").asText().trim();
                break;
            }
        }

        int ini = text.indexOf('{'), fin = text.lastIndexOf('}');
        if (ini >= 0 && fin > ini) text = text.substring(ini, fin + 1);

        JsonNode json = mapper.readTree(text);
        ParsedHoras p = new ParsedHoras();
        p.horas = json.path("horas").asDouble(0);
        JsonNode maq = json.path("maquina");
        p.maquinaMencionada = (maq.isNull() || maq.isMissingNode()) ? null : maq.asText(null);
        System.out.println("[WA] IA extrajo: horas=" + p.horas + " maquina=" + p.maquinaMencionada);
        return p;
    }

    private ParsedHoras parsearConRegex(String mensaje) {
        ParsedHoras p = new ParsedHoras();
        Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*h(?:oras?)?", Pattern.CASE_INSENSITIVE).matcher(mensaje);
        if (m.find()) {
            p.horas = Double.parseDouble(m.group(1).replace(',', '.'));
        }
        return p;
    }

    private ResponseEntity<String> twiml(String mensaje) {
        String seguro = mensaje
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                     "<Response><Message>" + seguro + "</Message></Response>";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        return new ResponseEntity<>(xml, headers, HttpStatus.OK);
    }

    static class ParsedHoras {
        double horas = 0;
        String maquinaMencionada = null;
    }
}
