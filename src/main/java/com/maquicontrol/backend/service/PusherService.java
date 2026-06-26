package com.maquicontrol.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maquicontrol.backend.repository.OperadorRepository;
import com.maquicontrol.backend.repository.UsuarioRepository;
import com.pusher.rest.Pusher;
import com.pusher.rest.data.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PusherService {

    private static final Logger log = LoggerFactory.getLogger(PusherService.class);

    private final Pusher pusher;

    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private OperadorRepository operadorRepo;
    @Autowired private ObjectMapper objectMapper;

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
            log.info("PusherService OK — cluster:{} appId:{}", cluster, appId);
        } else {
            pusher = null;
            log.warn("PusherService DESACTIVADO — appId='{}' key='{}' secret='{}'",
                appId.isBlank() ? "VACIO" : "ok",
                key.isBlank() ? "VACIO" : "ok",
                secret.isBlank() ? "VACIO" : "ok");
        }
    }

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
        if (pusher == null || userId == null) {
            log.debug("Pusher skip — pusher={} userId={}", pusher != null ? "ok" : "null", userId);
            return;
        }
        Long targetId = resolverAdminId(userId);
        try {
            // Convertir con Jackson primero para evitar el error de Gson con LocalDate en Java 21
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.convertValue(datos, Map.class);
            Result result = pusher.trigger("mc-" + targetId, evento, payload);
            log.info("Pusher emitido — canal=mc-{} evento={} http={} msg={}",
                targetId, evento, result.getHttpStatus(), result.getMessage());
        } catch (Exception e) {
            log.error("Pusher error — {}", e.getMessage());
        }
    }

    public void emitirEliminado(Long userId, String evento, Long id) {
        emitir(userId, evento, Map.of("id", id));
    }
}
