package com.maquicontrol.backend.model;

import com.maquicontrol.backend.converter.MapListConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "periodos")
public class Periodo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Convert(converter = MapListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<Map<String, Object>> anticipos = new ArrayList<>();

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public List<Map<String, Object>> getAnticipos() { return anticipos; }
    public void setAnticipos(List<Map<String, Object>> anticipos) { this.anticipos = anticipos; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
