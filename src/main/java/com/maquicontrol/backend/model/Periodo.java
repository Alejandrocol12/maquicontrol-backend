package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "periodos")
public class Periodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;
    private Long operadorId;
    private String estado = "activo";
    private String fechaInicio;
    private String fechaFin;
    private double horasTotal = 0;
    private double salarioBruto = 0;
    private double salarioNeto = 0;

    @Column(columnDefinition = "TEXT")
    private String nota;

    private Long desdeHoraId;

    private double anticipos = 0;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Long getOperadorId() { return operadorId; }
    public void setOperadorId(Long operadorId) { this.operadorId = operadorId; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(String fechaInicio) { this.fechaInicio = fechaInicio; }

    public String getFechaFin() { return fechaFin; }
    public void setFechaFin(String fechaFin) { this.fechaFin = fechaFin; }

    public double getHorasTotal() { return horasTotal; }
    public void setHorasTotal(double horasTotal) { this.horasTotal = horasTotal; }

    public double getSalarioBruto() { return salarioBruto; }
    public void setSalarioBruto(double salarioBruto) { this.salarioBruto = salarioBruto; }

    public double getSalarioNeto() { return salarioNeto; }
    public void setSalarioNeto(double salarioNeto) { this.salarioNeto = salarioNeto; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }

    public Long getDesdeHoraId() { return desdeHoraId; }
    public void setDesdeHoraId(Long desdeHoraId) { this.desdeHoraId = desdeHoraId; }

    public double getAnticipos() { return anticipos; }
    public void setAnticipos(double anticipos) { this.anticipos = anticipos; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
