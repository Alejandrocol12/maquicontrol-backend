package com.maquicontrol.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "maquinaria")
public class Maquina {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;

    private String nombre;
    private String tipo;
    private String placa;
    private int horometroActual;
    private String estado;
    private String operadorNombre;
    private double valorHoraOperador;
    private double valorHoraMaquina;

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }

    public int getHorometroActual() { return horometroActual; }
    public void setHorometroActual(int horometroActual) { this.horometroActual = horometroActual; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getOperadorNombre() { return operadorNombre; }
    public void setOperadorNombre(String operadorNombre) { this.operadorNombre = operadorNombre; }

    public double getValorHoraOperador() { return valorHoraOperador; }
    public void setValorHoraOperador(double valorHoraOperador) { this.valorHoraOperador = valorHoraOperador; }

    public double getValorHoraMaquina() { return valorHoraMaquina; }
    public void setValorHoraMaquina(double valorHoraMaquina) { this.valorHoraMaquina = valorHoraMaquina; }
}