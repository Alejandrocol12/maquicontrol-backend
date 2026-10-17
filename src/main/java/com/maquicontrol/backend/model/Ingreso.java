package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "ingresos")
public class Ingreso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;

    private String descripcion;
    private String tipoTrabajo;
    private double cantidad;
    private double valorUnitario;
    private double total;
    private LocalDate fecha;
    private String maquinaNombre;
    private Long faenaId;

    private Double horometroInicio;
    private Double horometroFin;

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getTipoTrabajo() { return tipoTrabajo; }
    public void setTipoTrabajo(String tipoTrabajo) { this.tipoTrabajo = tipoTrabajo; }

    public double getCantidad() { return cantidad; }
    public void setCantidad(double cantidad) { this.cantidad = cantidad; }

    public double getValorUnitario() { return valorUnitario; }
    public void setValorUnitario(double valorUnitario) { this.valorUnitario = valorUnitario; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getMaquinaNombre() { return maquinaNombre; }
    public void setMaquinaNombre(String maquinaNombre) { this.maquinaNombre = maquinaNombre; }

    public Long getFaenaId() { return faenaId; }
    public void setFaenaId(Long faenaId) { this.faenaId = faenaId; }

    // El horómetro se maneja con un decimal: se redondea para que no queden restos como 11620.699999999999
    private static Double r1(Double x) { return x == null ? null : Math.round(x * 10) / 10.0; }

    public Double getHorometroInicio() { return r1(horometroInicio); }
    public void setHorometroInicio(Double horometroInicio) { this.horometroInicio = r1(horometroInicio); }

    public Double getHorometroFin() { return r1(horometroFin); }
    public void setHorometroFin(Double horometroFin) { this.horometroFin = r1(horometroFin); }
}