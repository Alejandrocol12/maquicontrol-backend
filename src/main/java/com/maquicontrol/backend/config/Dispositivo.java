package com.maquicontrol.backend.config;

// Nombre legible del dispositivo a partir del User-Agent: "Chrome en Windows"
public final class Dispositivo {
    private Dispositivo() {}

    public static String describir(String ua) {
        if (ua == null || ua.isBlank()) return "Dispositivo desconocido";
        String u = ua.toLowerCase();
        String navegador = u.contains("edg/") ? "Edge"
                : u.contains("opr/") || u.contains("opera") ? "Opera"
                : u.contains("firefox") ? "Firefox"
                : u.contains("chrome") || u.contains("crios") ? "Chrome"
                : u.contains("safari") ? "Safari"
                : "Navegador";
        String sistema = u.contains("android") ? "Android"
                : u.contains("iphone") || u.contains("ipad") ? "iPhone"
                : u.contains("windows") ? "Windows"
                : u.contains("mac os") || u.contains("macintosh") ? "Mac"
                : u.contains("linux") ? "Linux"
                : "";
        return sistema.isEmpty() ? navegador : navegador + " en " + sistema;
    }
}
