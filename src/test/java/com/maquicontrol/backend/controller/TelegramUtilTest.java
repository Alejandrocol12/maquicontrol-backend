package com.maquicontrol.backend.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelegramUtilTest {

    @Test
    void numeroConPuntoDeMilesYDecimales() {
        assertEquals(9800.0, TelegramUtil.num("9.800"));
        assertEquals(9800.0, TelegramUtil.num("9,800"));
        assertEquals(7.5, TelegramUtil.num("7,5"));
        assertEquals(7.5, TelegramUtil.num("7.5"));
        assertEquals(1250000.0, TelegramUtil.num("1.250.000"));
    }

    @Test
    void montoYDescripcion() {
        TelegramUtil.Monto m = TelegramUtil.parsearMonto("$85.000 en filtro de aceite");
        assertNotNull(m);
        assertEquals(85000.0, m.valor());
        assertEquals("filtro de aceite", m.resto());
        assertNull(TelegramUtil.parsearMonto("sin números"));
    }

    @Test
    void tanqueoConPrecioPorGalon() {
        TelegramUtil.Tanqueo t = TelegramUtil.parsearTanqueo("20 galones a 9800");
        assertNotNull(t);
        assertEquals(20.0, t.galones());
        assertEquals(9800.0, t.precio());

        t = TelegramUtil.parsearTanqueo("compré 15,5 gal a $9.800");
        assertNotNull(t);
        assertEquals(15.5, t.galones());
        assertEquals(9800.0, t.precio());
    }

    @Test
    void tanqueoConTotalCalculaElPrecio() {
        TelegramUtil.Tanqueo t = TelegramUtil.parsearTanqueo("20 galones 196000");
        assertNotNull(t);
        assertEquals(20.0, t.galones());
        assertEquals(9800.0, t.precio());
    }

    @Test
    void tanqueoIncompletoNoSeInventa() {
        assertNull(TelegramUtil.parsearTanqueo("20 galones"));
        assertNull(TelegramUtil.parsearTanqueo("hola"));
        assertNull(TelegramUtil.parsearTanqueo("20 galones a 3"));
    }

    @Test
    void dineroYBarras() {
        assertEquals("$1.250.000", TelegramUtil.dinero(1250000));
        assertEquals("████░░░░", TelegramUtil.barra(4, 8, 8, '█', '░'));
        assertEquals("░░░░░░░░", TelegramUtil.barra(0, 8, 8, '█', '░'));
        assertEquals("████████", TelegramUtil.barra(20, 8, 8, '█', '░'));
    }

    @Test
    void escapaMarkdown() {
        assertEquals("CAT\\_320 \\*2\\*", TelegramUtil.md("CAT_320 *2*"));
    }
}
