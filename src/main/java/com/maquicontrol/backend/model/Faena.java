package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "faenas")
public class Faena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;
    private String maquinaNombre;
    private String nombreObra;
    private String cliente;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado; // "activa" | "cerrada"
    private String nota;

    private double totalIngresos;
    private double totalGastos;
    private double totalMantenimientos;
    private double utilidadNeta;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (estado == null) estado = "activa";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getMaquinaNombre() { return maquinaNombre; }
    public void setMaquinaNombre(String maquinaNombre) { this.maquinaNombre = maquinaNombre; }

    public String getNombreObra() { return nombreObra; }
    public void setNombreObra(String nombreObra) { this.nombreObra = nombreObra; }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }

    public double getTotalIngresos() { return totalIngresos; }
    public void setTotalIngresos(double totalIngresos) { this.totalIngresos = totalIngresos; }

    public double getTotalGastos() { return totalGastos; }
    public void setTotalGastos(double totalGastos) { this.totalGastos = totalGastos; }

    public double getTotalMantenimientos() { return totalMantenimientos; }
    public void setTotalMantenimientos(double totalMantenimientos) { this.totalMantenimientos = totalMantenimientos; }

    public double getUtilidadNeta() { return utilidadNeta; }
    public void setUtilidadNeta(double utilidadNeta) { this.utilidadNeta = utilidadNeta; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
