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

            String prompt = "Eres un asistente que extrae datos de facturas colombianas de taller o proveedor. " +
                "Analiza este documento y responde SOLO con JSON válido, sin explicación ni markdown. " +
                "Formato exacto: {\"descripcion\":\"descripción breve del gasto\"," +
                "\"monto\":0," +
                "\"categoria\":\"una de exactamente: Repuestos, Lubricantes, Combustible, Reparación, Otros\"," +
                "\"fecha\":\"YYYY-MM-DD\"}. " +
                "El monto debe ser número sin puntos ni símbolos. Si no puedes leer un campo usa null.";

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
            String text = root.path("content").get(0).path("text").asText();
            // Limpiar posibles bloques markdown que Claude pueda agregar
            text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

            return ResponseEntity.ok(mapper.readTree(text));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "No se pudo procesar el documento: " + e.getMessage()));
        }
    }
}
