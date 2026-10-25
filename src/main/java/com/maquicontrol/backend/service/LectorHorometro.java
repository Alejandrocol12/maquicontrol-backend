package com.maquicontrol.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

// Le pregunta a la IA qué hay en la foto y, si es un horómetro, qué número marca.
@Component
public class LectorHorometro {

    public record Analisis(String tipo, Double lectura, int confianza) {}

    private static final String MODELO = "claude-haiku-4-5-20251001";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public boolean disponible() {
        String k = System.getenv("ANTHROPIC_API_KEY");
        return k != null && !k.isBlank();
    }

    // Devuelve null si la IA no respondió algo útil
    public Analisis leer(byte[] jpeg) {
        try {
            ObjectNode img = mapper.createObjectNode();
            img.put("type", "image");
            ObjectNode src = mapper.createObjectNode();
            src.put("type", "base64");
            src.put("media_type", "image/jpeg");
            src.put("data", Base64.getEncoder().encodeToString(jpeg));
            img.set("source", src);

            ObjectNode txt = mapper.createObjectNode();
            txt.put("type", "text");
            txt.put("text",
                "Eres un asistente para maquinaria pesada. Mira la foto y responde ÚNICAMENTE con un JSON, sin texto extra: " +
                "{\"tipo\":\"horometro\",\"lectura\":8420.5,\"confianza\":96}. " +
                "tipo es exactamente uno de: horometro (el tablero o contador de horas de motor de una máquina), " +
                "factura (factura, recibo o ticket de compra), otra (cualquier otra cosa). " +
                "Si es horómetro, lectura es el número de horas que marca, como número con punto decimal; " +
                "el último dígito suele ser las décimas y a veces está en un recuadro aparte. " +
                "Si no puedes leer el número con claridad, da tu mejor estimación y baja la confianza, o usa null. " +
                "confianza es un entero de 0 a 100 sobre qué tan seguro estás de la lectura (o del tipo si no es horómetro).");

            ArrayNode contenido = mapper.createArrayNode();
            contenido.add(img);
            contenido.add(txt);
            ObjectNode msg = mapper.createObjectNode();
            msg.put("role", "user");
            msg.set("content", contenido);
            ArrayNode msgs = mapper.createArrayNode();
            msgs.add(msg);
            ObjectNode body = mapper.createObjectNode();
            body.put("model", MODELO);
            body.put("max_tokens", 200);
            body.set("messages", msgs);

            HttpResponse<String> res = http.send(HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("x-api-key", System.getenv("ANTHROPIC_API_KEY"))
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());

            String texto = "";
            for (JsonNode b : mapper.readTree(res.body()).path("content"))
                if ("text".equals(b.path("type").asText())) { texto = b.path("text").asText(); break; }
            int ini = texto.indexOf('{'), fin = texto.lastIndexOf('}');
            if (ini < 0 || fin <= ini) return null;
            JsonNode j = mapper.readTree(texto.substring(ini, fin + 1));

            String tipo = j.path("tipo").asText("otra").toLowerCase();
            Double lectura = null;
            JsonNode l = j.path("lectura");
            if (l.isNumber()) lectura = l.asDouble();
            else if (l.isTextual()) {
                try { lectura = Double.parseDouble(l.asText().replace(",", ".").trim()); } catch (Exception ignored) {}
            }
            if (lectura != null && (lectura < 0 || lectura > 999999)) lectura = null;
            int conf = Math.max(0, Math.min(100, j.path("confianza").asInt(0)));
            return new Analisis(tipo, lectura, conf);
        } catch (Exception e) {
            System.out.println("[IA] Error leyendo horómetro: " + e.getMessage());
            return null;
        }
    }

    // Reduce la foto (lado mayor 1280 px, JPEG): pesa poco en la base de datos y alcanza para leer el tablero
    public static byte[] reducir(byte[] datos) {
        try {
            BufferedImage origen = ImageIO.read(new ByteArrayInputStream(datos));
            if (origen == null) return datos;
            double escala = Math.min(1.0, 1280.0 / Math.max(origen.getWidth(), origen.getHeight()));
            int w = Math.max(1, (int) Math.round(origen.getWidth() * escala));
            int h = Math.max(1, (int) Math.round(origen.getHeight() * escala));
            BufferedImage salida = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = salida.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(origen, 0, 0, w, h, null);
            g.dispose();

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(0.8f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(salida, null, null), p);
            }
            writer.dispose();
            return out.toByteArray();
        } catch (Exception e) {
            return datos;
        }
    }
}
