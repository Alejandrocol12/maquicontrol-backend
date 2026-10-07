package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Un corte es un tramo de un periodo (faena) entre dos fechas que digita el admin: se suman
// las horas de ese tramo para saber cuanto se le cobra al cliente. Las mismas fechas sirven
// para liquidarle al operador cuando conOperador es true.
@Entity
@Table(name = "cortes")
public class Corte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;
    private Long faenaId;
    private String maquinaNombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    // Foto de lo que sumaba el tramo al momento de hacer el corte. El frontend recalcula en
    // vivo y, si no coincide, avisa que el corte cambio.
    private double horas;
    private double total;

    // Cobro creado en Pagos Clientes para este corte (si se pidio)
    private Long pagoClienteId;

    private boolean conOperador = true;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Long getFaenaId() { return faenaId; }
    public void setFaenaId(Long faenaId) { this.faenaId = faenaId; }

    public String getMaquinaNombre() { return maquinaNombre; }
    public void setMaquinaNombre(String maquinaNombre) { this.maquinaNombre = maquinaNombre; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public double getHoras() { return horas; }
    public void setHoras(double horas) { this.horas = horas; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public Long getPagoClienteId() { return pagoClienteId; }
    public void setPagoClienteId(Long pagoClienteId) { this.pagoClienteId = pagoClienteId; }

    public boolean isConOperador() { return conOperador; }
    public void setConOperador(boolean conOperador) { this.conOperador = conOperador; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
