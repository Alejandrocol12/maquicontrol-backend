package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "pagos_clientes", indexes = @Index(name = "idx_pagos_clientes_usuario", columnList = "usuarioId"))
public class PagoCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;

    private String cliente;
    private String maquinaNombre;
    private String descripcion;
    private double valorTotal;
    private double valorPagado;
    private double saldoPendiente;
    private String estado;
    private LocalDate fecha;

    // Cómo se pagó (transferencia, efectivo...) y número del comprobante; opcionales
    @Column(length = 40)
    private String metodoPago;

    @Column(length = 60)
    private String comprobante;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public String getMaquinaNombre() { return maquinaNombre; }
    public void setMaquinaNombre(String maquinaNombre) { this.maquinaNombre = maquinaNombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public double getValorTotal() { return valorTotal; }
    public void setValorTotal(double valorTotal) { this.valorTotal = valorTotal; }

    public double getValorPagado() { return valorPagado; }
    public void setValorPagado(double valorPagado) { this.valorPagado = valorPagado; }

    public double getSaldoPendiente() { return saldoPendiente; }
    public void setSaldoPendiente(double saldoPendiente) { this.saldoPendiente = saldoPendiente; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public String getComprobante() { return comprobante; }
    public void setComprobante(String comprobante) { this.comprobante = comprobante; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
}