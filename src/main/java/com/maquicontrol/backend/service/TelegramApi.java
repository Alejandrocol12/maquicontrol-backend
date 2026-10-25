package com.maquicontrol.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

// Llamadas a la API del bot de Telegram que usa el flujo de la foto del horómetro (mensajes con botones, fotos y descargas).
@Component
public class TelegramApi {

    public record Boton(String texto, String dato) {}

    @Value("${telegram.bot.token:}")
    private String botToken;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public boolean activo() { return botToken != null && !botToken.isBlank(); }

    private String url(String metodo) { return "https://api.telegram.org/bot" + botToken + "/" + metodo; }

    private JsonNode llamar(String metodo, ObjectNode cuerpo) {
        if (!activo()) return null;
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder().uri(URI.create(url(metodo)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(cuerpo))).build(),
                    HttpResponse.BodyHandlers.ofString());
            return mapper.readTree(r.body());
        } catch (Exception e) {
            System.out.println("[TG] Error en " + metodo + ": " + e.getMessage());
            return null;
        }
    }

    private ObjectNode teclado(List<List<Boton>> filas) {
        ArrayNode kb = mapper.createArrayNode();
        for (List<Boton> fila : filas) {
            ArrayNode f = mapper.createArrayNode();
            for (Boton b : fila) {
                ObjectNode o = mapper.createObjectNode();
                o.put("text", b.texto());
                o.put("callback_data", b.dato());
                f.add(o);
            }
            kb.add(f);
        }
        ObjectNode markup = mapper.createObjectNode();
        markup.set("inline_keyboard", kb);
        return markup;
    }

    // Texto en HTML (<b>, <i>) con botones opcionales debajo del mensaje
    public void enviar(long chatId, String html, List<List<Boton>> botones) {
        ObjectNode body = mapper.createObjectNode();
        body.put("chat_id", chatId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        if (botones != null && !botones.isEmpty()) body.set("reply_markup", teclado(botones));
        llamar("sendMessage", body);
    }

    // Reenvía una foto que ya está en Telegram (por su file_id) con un pie y botones
    public void enviarFoto(long chatId, String fileId, String html, List<List<Boton>> botones) {
        ObjectNode body = mapper.createObjectNode();
        body.put("chat_id", chatId);
        body.put("photo", fileId);
        body.put("caption", html);
        body.put("parse_mode", "HTML");
        if (botones != null && !botones.isEmpty()) body.set("reply_markup", teclado(botones));
        llamar("sendPhoto", body);
    }

    // Quita el círculo de carga del botón que se tocó
    public void responderCallback(String callbackId, String texto) {
        ObjectNode body = mapper.createObjectNode();
        body.put("callback_query_id", callbackId);
        if (texto != null) body.put("text", texto);
        llamar("answerCallbackQuery", body);
    }

    // Deja un mensaje sin botones para que no se pulse dos veces
    public void quitarBotones(long chatId, long messageId) {
        ObjectNode body = mapper.createObjectNode();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.set("reply_markup", mapper.createObjectNode().set("inline_keyboard", mapper.createArrayNode()));
        llamar("editMessageReplyMarkup", body);
    }

    public byte[] descargar(String fileId) {
        if (!activo()) return null;
        try {
            HttpResponse<String> meta = http.send(
                    HttpRequest.newBuilder().uri(URI.create(url("getFile") + "?file_id=" + fileId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            String path = mapper.readTree(meta.body()).path("result").path("file_path").asText();
            if (path.isBlank()) return null;
            return http.send(HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/file/bot" + botToken + "/" + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()).body();
        } catch (Exception e) {
            System.out.println("[TG] Error descargando archivo: " + e.getMessage());
            return null;
        }
    }
}
