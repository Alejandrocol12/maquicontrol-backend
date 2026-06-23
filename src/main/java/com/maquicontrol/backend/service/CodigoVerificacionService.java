package com.maquicontrol.backend.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CodigoVerificacionService {

    private record CodigoEntry(String codigo, long expiry) {}
    private final Map<String, CodigoEntry> store = new ConcurrentHashMap<>();
    private final Random rng = new Random();

    public String generar(String email) {
        String codigo = String.format("%06d", rng.nextInt(1_000_000));
        store.put(email.toLowerCase(), new CodigoEntry(codigo, System.currentTimeMillis() + 15 * 60_000L));
        return codigo;
    }

    public boolean verificar(String email, String codigo) {
        CodigoEntry e = store.get(email.toLowerCase());
        if (e == null || System.currentTimeMillis() > e.expiry()) return false;
        if (e.codigo().equals(codigo)) {
            store.remove(email.toLowerCase());
            return true;
        }
        return false;
    }
}
