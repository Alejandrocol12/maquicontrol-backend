package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

// Registro puramente informativo de cuánto se le ha pagado a un operador -- no se relaciona
// con Salarios ni con ningún otro total, es solo una bitácora que lleva el admin.
@Entity
@Table(name = "pagos_operador")
public class PagoOperador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;

    private String operadorNombre;
    private String descripcion;
    private double monto;
    private LocalDate fecha;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getOperadorNombre() { return operadorNombre; }
    public void setOperadorNombre(String operadorNombre) { this.operadorNombre = operadorNombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
}
