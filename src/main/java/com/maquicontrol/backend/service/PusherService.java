package com.maquicontrol.backend.service;

import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.repository.UsuarioRepository;
import com.pusher.rest.Pusher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PusherService {

    private final Pusher pusher;

    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private OperadorRepository operadorRepo;

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

    // Si userId pertenece a un operador, resuelve al userId del admin dueño.
    private Long resolverAdminId(Long userId) {
        if (userId == null) return null;
        return usuarioRepo.findById(userId)
            .map(u -> u.getOperadorId())
            .filter(oid -> oid != null)
            .flatMap(oid -> operadorRepo.findById(oid))
            .map(op -> op.getUsuarioId())
            .orElse(userId);
    }

    public void emitir(Long userId, String evento, Object datos) {
        if (pusher == null || userId == null) return;
        Long targetId = resolverAdminId(userId);
        try {
            pusher.trigger("mc-" + targetId, evento, datos);
        } catch (Exception ignored) {}
    }

    public void emitirEliminado(Long userId, String evento, Long id) {
        emitir(userId, evento, Map.of("id", id));
    }
}
