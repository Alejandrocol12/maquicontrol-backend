package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "salarios")
public class Salario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;

    private String operadorNombre;
    private String maquinaNombre;
    private double horasTrabajadas;
    private double valorHora;
    private double totalBruto;
    private double anticipos;
    private double totalNeto;
    private String estado;
    private LocalDate fecha;
    private Long faenaId;
    private Long gastoGeneradoId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getOperadorNombre() { return operadorNombre; }
    public void setOperadorNombre(String operadorNombre) { this.operadorNombre = operadorNombre; }

    public String getMaquinaNombre() { return maquinaNombre; }
    public void setMaquinaNombre(String maquinaNombre) { this.maquinaNombre = maquinaNombre; }

    public double getHorasTrabajadas() { return horasTrabajadas; }
    public void setHorasTrabajadas(double horasTrabajadas) { this.horasTrabajadas = horasTrabajadas; }

    public double getValorHora() { return valorHora; }
    public void setValorHora(double valorHora) { this.valorHora = valorHora; }

    public double getTotalBruto() { return totalBruto; }
    public void setTotalBruto(double totalBruto) { this.totalBruto = totalBruto; }

    public double getAnticipos() { return anticipos; }
    public void setAnticipos(double anticipos) { this.anticipos = anticipos; }

    public double getTotalNeto() { return totalNeto; }
    public void setTotalNeto(double totalNeto) { this.totalNeto = totalNeto; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public Long getFaenaId() { return faenaId; }
    public void setFaenaId(Long faenaId) { this.faenaId = faenaId; }

    public Long getGastoGeneradoId() { return gastoGeneradoId; }
    public void setGastoGeneradoId(Long gastoGeneradoId) { this.gastoGeneradoId = gastoGeneradoId; }
}