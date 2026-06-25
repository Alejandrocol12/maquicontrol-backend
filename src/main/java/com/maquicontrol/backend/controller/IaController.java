package com.maquicontrol.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class IaController {

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-3-5-haiku-20241022";
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

            // Bloque de contenido según tipo de archivo
            Map<String, Object> contenido;
            if (esPdf) {
                contenido = Map.of(
                    "type", "document",
                    "source", Map.of("type", "base64", "media_type", "application/pdf", "data", base64)
                );
            } else {
                contenido = Map.of(
                    "type", "image",
                    "source", Map.of("type", "base64", "media_type", mediaType, "data", base64)
                );
            }

            String prompt = "Eres un asistente experto en facturas colombianas de taller y proveedores. " +
                "Analiza este documento y extrae los datos principales. " +
                "Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional, sin markdown. " +
                "Formato exacto (usa estos campos exactos): " +
                "{\"descripcion\":\"descripción breve del servicio o producto\"," +
                "\"monto\":150000," +
                "\"categoria\":\"Repuestos\"," +
                "\"fecha\":\"2024-01-15\"}. " +
                "Reglas: " +
                "- descripcion: texto corto describiendo qué se compró o reparó. " +
                "- monto: número entero en pesos colombianos, sin puntos ni símbolos. " +
                "- categoria: DEBE ser exactamente una de estas opciones: Repuestos, Lubricantes, Combustible, Reparación, Otros. " +
                "- fecha: formato YYYY-MM-DD. Si no ves fecha clara, usa la fecha de hoy. " +
                "- Si no puedes leer algún campo, usa tu mejor estimación. Solo usa null si el documento no tiene ninguna información relevante.";

            Map<String, Object> requestBody = Map.of(
                "model", MODEL,
                "max_tokens", 512,
                "messages", List.of(Map.of(
                    "role", "user",
                    "content", List.of(
                        contenido,
                        Map.of("type", "text", "text", prompt)
                    )
                ))
            );

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ANTHROPIC_URL))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = mapper.readTree(response.body());

            // Verificar que la API respondió correctamente
            if (root.has("error")) {
                String errMsg = root.path("error").path("message").asText(root.path("error").asText());
                return ResponseEntity.status(502).body(Map.of("error", "Anthropic: " + errMsg));
            }

            String text = root.path("content").get(0).path("text").asText();
            System.out.println("[IA] Claude raw: " + text);
            // Quitar bloques markdown y extraer el objeto JSON
            text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            // Si hay texto antes del {, extraer sólo el bloque JSON
            int inicio = text.indexOf('{');
            int fin    = text.lastIndexOf('}');
            if (inicio >= 0 && fin > inicio) {
                text = text.substring(inicio, fin + 1);
            }

            return ResponseEntity.ok(mapper.readTree(text));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "No se pudo procesar el documento: " + e.getMessage()));
        }
    }
}
