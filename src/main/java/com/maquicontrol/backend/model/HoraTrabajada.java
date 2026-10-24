package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "horas_trabajadas", indexes = @Index(name = "idx_horas_trabajadas_usuario", columnList = "usuarioId"))
public class HoraTrabajada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;
    private Long operadorId;
    private Long maquinaId;
    private String operadorNombre;
    private String maquinaNombre;
    private double horas;
    private String horaEntrada;
    private String horaSalida;
    private double horometroInicio;
    private double horometroFin;
    private double valorHora;
    private LocalDate fecha;
    private Long faenaId;
    private Long gastoGeneradoId;
    // Ingreso que se creo junto con esta hora (si vino del registro de trabajo de una maquina).
    // Permite borrar esta hora automaticamente si se borra ese ingreso, y evitar que quede huerfana.
    private Long ingresoId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

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

    // El horómetro se maneja con un decimal: se redondea para que no queden restos como 11620.699999999999
    private static double r1(double x) { return Math.round(x * 10) / 10.0; }

    public double getHorometroInicio() { return r1(horometroInicio); }
    public void setHorometroInicio(double horometroInicio) { this.horometroInicio = r1(horometroInicio); }

    public double getHorometroFin() { return r1(horometroFin); }
    public void setHorometroFin(double horometroFin) { this.horometroFin = r1(horometroFin); }

    public double getValorHora() { return valorHora; }
    public void setValorHora(double valorHora) { this.valorHora = valorHora; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public Long getFaenaId() { return faenaId; }
    public void setFaenaId(Long faenaId) { this.faenaId = faenaId; }

    public Long getGastoGeneradoId() { return gastoGeneradoId; }
    public void setGastoGeneradoId(Long gastoGeneradoId) { this.gastoGeneradoId = gastoGeneradoId; }

    public Long getIngresoId() { return ingresoId; }
    public void setIngresoId(Long ingresoId) { this.ingresoId = ingresoId; }
}