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
    private double horometroActual;
    private String estado;
    private String operadorNombre;
    private double valorHoraOperador;
    private double valorHoraMaquina;

    private Double latitud;
    private Double longitud;
    private String ubicacionNombre;

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

    public double getHorometroActual() { return horometroActual; }
    public void setHorometroActual(double horometroActual) { this.horometroActual = horometroActual; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getOperadorNombre() { return operadorNombre; }
    public void setOperadorNombre(String operadorNombre) { this.operadorNombre = operadorNombre; }

    public double getValorHoraOperador() { return valorHoraOperador; }
    public void setValorHoraOperador(double valorHoraOperador) { this.valorHoraOperador = valorHoraOperador; }

    public double getValorHoraMaquina() { return valorHoraMaquina; }
    public void setValorHoraMaquina(double valorHoraMaquina) { this.valorHoraMaquina = valorHoraMaquina; }

    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }

    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }

    public String getUbicacionNombre() { return ubicacionNombre; }
    public void setUbicacionNombre(String ubicacionNombre) { this.ubicacionNombre = ubicacionNombre; }
}