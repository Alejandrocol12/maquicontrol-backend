package com.maquicontrol.backend.service;

import com.pusher.rest.Pusher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PusherService {

    private final Pusher pusher;

    public PusherService(
        @Value("${pusher.app-id:}") String appId,
        @Value("${pusher.key:}")    String key,
        @Value("${pusher.secret:}") String secret,
        @Value("${pusher.cluster:us2}") String cluster
    ) {
        if (!appId.isBlank() && !key.isBlank() && !secret.isBlank()) {
            pusher = new Pusher(appId, key, secret);
            pusher.setCluster(cluster);
            pusher.setEncrypted(true);
        } else {
            pusher = null;
        }
    }

    // Emite un evento al canal del usuario. Si Pusher no está configurado, no hace nada.
    public void emitir(Long userId, String evento, Object datos) {
        if (pusher == null || userId == null) return;
        try {
            pusher.trigger("mc-" + userId, evento, datos);
        } catch (Exception ignored) {}
    }

    public void emitirEliminado(Long userId, String evento, Long id) {
        emitir(userId, evento, Map.of("id", id));
    }
}
