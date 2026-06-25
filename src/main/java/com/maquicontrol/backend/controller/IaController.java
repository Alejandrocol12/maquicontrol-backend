package com.maquicontrol.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;

@RestController
public class IaController {

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private final ObjectMapper mapper = new ObjectMapper();

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
}
