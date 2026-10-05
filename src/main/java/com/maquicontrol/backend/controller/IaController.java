package com.maquicontrol.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maquicontrol.backend.model.Maquina;
import com.maquicontrol.backend.service.MaquinaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;

@RestController
public class IaController {

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired private MaquinaService maquinaService;

    @PostMapping("/api/ia/leer-factura")
    public ResponseEntity<?> leerFactura(@RequestParam("file") MultipartFile file) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("error", "API de IA no configurada"));
        }
        try {
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            String mediaType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            boolean esPdf = "application/pdf".equals(mediaType);

            // Construir el bloque de contenido con Jackson ObjectNode
            ObjectNode contentBlock = mapper.createObjectNode();
            if (esPdf) {
                contentBlock.put("type", "document");
                ObjectNode source = mapper.createObjectNode();
                source.put("type", "base64");
                source.put("media_type", "application/pdf");
                source.put("data", base64);
                contentBlock.set("source", source);
            } else {
                contentBlock.put("type", "image");
                ObjectNode source = mapper.createObjectNode();
                source.put("type", "base64");
                source.put("media_type", mediaType);
                source.put("data", base64);
                contentBlock.set("source", source);
            }

            String prompt = "Eres un asistente experto en facturas colombianas de taller y proveedores. " +
                "Analiza este documento y extrae los datos principales. " +
                "Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional, sin markdown. " +
                "Formato exacto: {\"descripcion\":\"descripción breve\",\"monto\":150000,\"categoria\":\"Repuestos\",\"fecha\":\"2024-01-15\"}. " +
                "categoria DEBE ser exactamente una de: Repuestos, Lubricantes, Combustible, Reparación, Otros. " +
                "monto es número entero sin puntos ni símbolos. fecha en formato YYYY-MM-DD.";

            ObjectNode textBlock = mapper.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text", prompt);

            ArrayNode contentArray = mapper.createArrayNode();
            contentArray.add(contentBlock);
            contentArray.add(textBlock);

            ObjectNode message = mapper.createObjectNode();
            message.put("role", "user");
            message.set("content", contentArray);

            ArrayNode messages = mapper.createArrayNode();
            messages.add(message);

            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", MODEL);
            requestBody.put("max_tokens", 2048);
            requestBody.set("messages", messages);

            String jsonBody = mapper.writeValueAsString(requestBody);
            System.out.println("[IA] Enviando a Anthropic, modelo=" + MODEL + ", mediaType=" + mediaType);
            System.out.println("[IA] JSON (sin base64): " + jsonBody.substring(0, Math.min(300, jsonBody.length())));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ANTHROPIC_URL))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[IA] HTTP status: " + response.statusCode());
            System.out.println("[IA] FULL response: " + response.body());

            JsonNode root = mapper.readTree(response.body());

            if (root.has("error")) {
                String errMsg = root.path("error").path("message").asText(root.path("error").asText());
                System.out.println("[IA] Error Anthropic: " + errMsg);
                return ResponseEntity.status(502).body(Map.of("error", errMsg));
            }

            // Buscar el primer bloque de tipo "text" (puede haber thinking blocks antes)
            String text = "";
            JsonNode contentArr = root.path("content");
            for (JsonNode block : contentArr) {
                if ("text".equals(block.path("type").asText())) {
                    text = block.path("text").asText();
                    break;
                }
            }
            System.out.println("[IA] Texto extraído (raw): '" + text + "'");

            // Extraer el bloque JSON ignorando markdown y texto adicional
            text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            int inicio = text.indexOf('{');
            int fin    = text.lastIndexOf('}');
            if (inicio >= 0 && fin > inicio) {
                text = text.substring(inicio, fin + 1);
            }

            // Validar que el JSON es parseable
            mapper.readTree(text); // lanza excepción si es inválido
            System.out.println("[IA] JSON final: " + text);

            // Devolver el JSON directamente como string para evitar re-serialización
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(text, headers, HttpStatus.OK);

        } catch (Exception e) {
            System.out.println("[IA] Exception: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Entiende una frase escrita como se habla en obra ("le metí 8 y media a la retro",
    // "20 lucas de ACPM ayer") y la convierte en un registro para que el usuario lo
    // revise antes de guardarlo. No guarda nada: solo interpreta.
    @PostMapping("/api/ia/interpretar")
    public ResponseEntity<?> interpretar(@RequestBody Map<String, String> body, Authentication auth) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("error", "API de IA no configurada"));
        }
        String texto = body.getOrDefault("texto", "").trim();
        if (texto.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Escribe qué pasó"));
        if (texto.length() > 400) texto = texto.substring(0, 400);

        Long userId = (Long) auth.getPrincipal();
        StringBuilder flota = new StringBuilder();
        for (Maquina m : maquinaService.obtenerTodas(userId)) {
            flota.append("- \"").append(m.getNombre()).append("\" (tipo: ").append(m.getTipo() == null ? "?" : m.getTipo()).append(")\n");
        }
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        String prompt = "Eres el asistente de MaquiControl, una app para negocios colombianos de alquiler de maquinaria pesada. " +
            "Un usuario escribió (o dictó) esta frase sobre algo que pasó en obra: \"" + texto.replace("\"", "'") + "\"\n\n" +
            "Hoy es " + hoy + " (" + hoy.getDayOfWeek() + "). Máquinas de la empresa:\n" + flota +
            "\nConviértela en un registro. Guía de cómo se habla en Colombia:\n" +
            "- Dinero: \"luca\"/\"lucas\" y \"barra\"/\"barras\" = mil pesos (20 lucas = 20000). \"palo\"/\"palos\", \"melón\"/\"melones\" y \"millón\" = un millón " +
            "(2 palos = 2000000; un palo y medio = 1500000; 2 palos 300 = 2300000). \"250 mil\", \"250k\" y \"250.000\" = 250000. \"media luca\" = 500.\n" +
            "- Horas: \"8 y media\" = 8.5, \"8 y cuarto\" = 8.25, \"7 y tres cuartos\" = 7.75, \"media hora\" = 0.5. Frases como \"le metí\", \"trabajó\", \"echó\", \"hizo\" seguidas de horas son horas trabajadas.\n" +
            "- Máquinas: \"la retro\", \"la retroexcavadora\", \"la pala\", \"la excavadora\" = Excavadora; \"el bull\", \"el buldócer\", \"la topadora\" = Bulldozer; también \"la grúa\", \"la volqueta\", \"la moto(niveladora)\". " +
            "Escoge la máquina de la lista que corresponda por nombre o por tipo. Si hay dos o más del mismo tipo y la frase no dice cuál, deja maquina en null.\n" +
            "- Gastos: \"ACPM\", \"diésel\", \"gasoil\", \"tanqueé\", \"le eché combustible\" = categoría Combustible; \"aceite\", \"grasa\", \"hidráulico\", \"valvulina\" = Lubricantes; " +
            "\"repuesto\", \"manguera\", \"filtro\", \"llanta\", \"rodamiento\", \"uña\", \"diente\" = Repuestos; \"taller\", \"soldadura\", \"mecánico\", \"arreglo\", \"me cobraron por arreglar\" = Reparación; lo demás = Otros.\n" +
            "- Fechas: \"hoy\", \"ayer\", \"antier\"/\"anteayer\", \"el lunes\" (el más reciente ya pasado), \"el 12\" (de este mes, o del anterior si ese día aún no llega). Sin fecha = hoy.\n" +
            "No inventes nada: si un dato no está claro, ponlo en null y explica en \"nota\" qué falta, en una frase corta y amable en español de Colombia.\n\n" +
            "Responde ÚNICAMENTE con un objeto JSON válido, sin markdown, con este formato exacto:\n" +
            "{\"tipo\":\"horas\" o \"gasto\" o \"desconocido\",\"maquina\":\"nombre exacto de la lista o null\",\"horas\":número o null," +
            "\"monto\":entero en pesos o null,\"categoria\":\"Combustible|Lubricantes|Repuestos|Reparación|Otros\" o null," +
            "\"descripcion\":\"descripción corta y clara, o null\",\"fecha\":\"YYYY-MM-DD\",\"nota\":\"texto o null\"}";

        try {
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
            requestBody.put("model", MODEL);
            requestBody.put("max_tokens", 600);
            requestBody.set("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ANTHROPIC_URL))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = mapper.readTree(response.body());
            if (root.has("error")) {
                String errMsg = root.path("error").path("message").asText(root.path("error").asText());
                return ResponseEntity.status(502).body(Map.of("error", errMsg));
            }
            String text = "";
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) { text = block.path("text").asText(); break; }
            }
            text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            int inicio = text.indexOf('{');
            int fin = text.lastIndexOf('}');
            if (inicio >= 0 && fin > inicio) text = text.substring(inicio, fin + 1);
            mapper.readTree(text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new ResponseEntity<>(text, headers, HttpStatus.OK);
        } catch (Exception e) {
            System.out.println("[IA] interpretar falló: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "No pude entender la frase, intenta escribirla de otra forma"));
        }
    }
}
