package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "combustible")
public class Combustible {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;

    private String maquinaNombre;
    private double galones;
    private double precioPorGalon;
    private double total;
    private int horometroAlCargar;
    private LocalDate fecha;
    private Long faenaId;
    private Long gastoGeneradoId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getMaquinaNombre() { return maquinaNombre; }
    public void setMaquinaNombre(String maquinaNombre) { this.maquinaNombre = maquinaNombre; }

    public double getGalones() { return galones; }
    public void setGalones(double galones) { this.galones = galones; }

    public double getPrecioPorGalon() { return precioPorGalon; }
    public void setPrecioPorGalon(double precioPorGalon) { this.precioPorGalon = precioPorGalon; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public int getHorometroAlCargar() { return horometroAlCargar; }
    public void setHorometroAlCargar(int horometroAlCargar) { this.horometroAlCargar = horometroAlCargar; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public Long getFaenaId() { return faenaId; }
    public void setFaenaId(Long faenaId) { this.faenaId = faenaId; }

    public Long getGastoGeneradoId() { return gastoGeneradoId; }
    public void setGastoGeneradoId(Long gastoGeneradoId) { this.gastoGeneradoId = gastoGeneradoId; }
}