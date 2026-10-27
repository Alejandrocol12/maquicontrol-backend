package com.maquicontrol.backend.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Funciones puras del bot de Telegram (sin base de datos ni red) para poder probarlas solas.
final class TelegramUtil {

    private TelegramUtil() {}

    private static final Pattern NUMERO = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern GALONES = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:gal\\w*|gl\\b)", Pattern.CASE_INSENSITIVE);

    record Monto(double valor, String resto) {}
    record Tanqueo(double galones, double precio) {}

    // "9.800" y "9,800" son nueve mil ochocientos; "7,5" y "7.5" son siete y medio
    static Double num(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.matches("\\d{1,3}([.,]\\d{3})+")) t = t.replaceAll("[.,]", "");
        else t = t.replace(',', '.');
        try { return Double.parseDouble(t); } catch (NumberFormatException e) { return null; }
    }

    // Primer número del texto y lo que queda del texto sin él, para usarlo de descripción
    static Monto parsearMonto(String texto) {
        if (texto == null) return null;
        Matcher m = NUMERO.matcher(texto);
        while (m.find()) {
            Double v = num(m.group());
            if (v == null || v <= 0) continue;
            String resto = (texto.substring(0, m.start()) + " " + texto.substring(m.end()))
                .replace("$", " ").replaceAll("(?i)\\b(pesos|cop)\\b", " ").replaceAll("\\s+", " ").trim();
            resto = resto.replaceFirst("(?i)^(de|en|por|para|el|la|un|una)\\s+", "").trim();
            return new Monto(v, resto);
        }
        return null;
    }

    // "20 galones a 9800", "20 gal $9.800", "20 galones 196000" (total). Devuelve null si no se entiende.
    static Tanqueo parsearTanqueo(String texto) {
        if (texto == null) return null;
        List<int[]> posiciones = new ArrayList<>();
        List<Double> valores = new ArrayList<>();
        Matcher m = NUMERO.matcher(texto);
        while (m.find()) {
            Double v = num(m.group());
            if (v == null || v <= 0) continue;
            posiciones.add(new int[]{m.start(), m.end()});
            valores.add(v);
        }
        if (valores.size() < 2) return null;

        int idxGal = 0;
        Matcher g = GALONES.matcher(texto);
        if (g.find()) {
            for (int i = 0; i < posiciones.size(); i++) {
                if (posiciones.get(i)[0] == g.start(1)) { idxGal = i; break; }
            }
        }
        double galones = valores.get(idxGal);
        double precio = 0;
        for (int i = 0; i < valores.size(); i++) {
            if (i != idxGal) { precio = valores.get(i); break; }
        }
        if (galones <= 0 || galones > 2000) return null;
        if (precio >= 50000) precio = precio / galones;
        if (precio < 500) return null;
        return new Tanqueo(galones, Math.round(precio * 100) / 100.0);
    }

    static String dinero(double v) {
        return "$" + String.format(Locale.US, "%,.0f", v).replace(",", ".");
    }

    static String barra(double valor, double max, int ancho, char lleno, char vacio) {
        int n = max <= 0 ? 0 : (int) Math.round(Math.min(1.0, Math.max(0.0, valor / max)) * ancho);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ancho; i++) sb.append(i < n ? lleno : vacio);
        return sb.toString();
    }

    // Telegram (Markdown clásico) falla si un nombre trae _ * ` o [ sin escapar
    static String md(String s) {
        if (s == null) return "";
        return s.replace("_", "\\_").replace("*", "\\*").replace("`", "\\`").replace("[", "\\[");
    }
}
