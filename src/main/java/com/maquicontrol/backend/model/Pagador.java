package com.maquicontrol.backend.model;

import jakarta.persistence.*;

// Persona que paga gastos (socio, operador, la caja, un proveedor...). La lista la arma cada usuario.
@Entity
@Table(name = "pagadores", indexes = @Index(name = "idx_pagadores_usuario", columnList = "usuarioId"))
public class Pagador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;

    @Column(length = 80)
    private String nombre;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
