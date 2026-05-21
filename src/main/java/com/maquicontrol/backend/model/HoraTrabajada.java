package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "horas_trabajadas")
public class HoraTrabajada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long operadorId;
    private Long maquinaId;
    private String operadorNombre;
    private String maquinaNombre;
    private double horas;
    private String horaEntrada;
    private String horaSalida;
    private int horometroInicio;
    private double horometroFin;
    private double valorHora;
    private LocalDate fecha;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOperadorId() { return operadorId; }
    public void setOperadorId(Long operadorId) { this.operadorId = operadorId; }

    public Long getMaquinaId() { return maquinaId; }
    public void setMaquinaId(Long maquinaId) { this.maquinaId = maquinaId; }

    public String getOperadorNombre() { return operadorNombre; }
    public void setOperadorNombre(String operadorNombre) { this.operadorNombre = operadorNombre; }

    public String getMaquinaNombre() { return maquinaNombre; }
    public void setMaquinaNombre(String maquinaNombre) { this.maquinaNombre = maquinaNombre; }

    public double getHoras() { return horas; }
    public void setHoras(double horas) { this.horas = horas; }

    public String getHoraEntrada() { return horaEntrada; }
    public void setHoraEntrada(String horaEntrada) { this.horaEntrada = horaEntrada; }

    public String getHoraSalida() { return horaSalida; }
    public void setHoraSalida(String horaSalida) { this.horaSalida = horaSalida; }

    public int getHorometroInicio() { return horometroInicio; }
    public void setHorometroInicio(int horometroInicio) { this.horometroInicio = horometroInicio; }

    public double getHorometroFin() { return horometroFin; }
    public void setHorometroFin(double horometroFin) { this.horometroFin = horometroFin; }

    public double getValorHora() { return valorHora; }
    public void setValorHora(double valorHora) { this.valorHora = valorHora; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
}