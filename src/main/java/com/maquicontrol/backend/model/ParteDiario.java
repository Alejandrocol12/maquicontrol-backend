package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

// Anotaciones de un día de una máquina: cómo operó (trabajo normal, lluvia, mantenimiento, descanso)
// y las observaciones. Las horas y el dinero salen de los ingresos del día, no se guardan aquí.
@Entity
@Table(name = "partes_diarios", uniqueConstraints = @UniqueConstraint(columnNames = {"usuarioId", "maquinaNombre", "fecha"}))
public class ParteDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;
    private String maquinaNombre;
    private LocalDate fecha;

    // NORMAL | LLUVIA | MANTENIMIENTO | DESCANSO
    private String estadoOperacion;

    @Column(length = 300)
    private String actividad;

    @Column(length = 1000)
    private String observaciones;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getMaquinaNombre() { return maquinaNombre; }
    public void setMaquinaNombre(String maquinaNombre) { this.maquinaNombre = maquinaNombre; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getEstadoOperacion() { return estadoOperacion; }
    public void setEstadoOperacion(String estadoOperacion) { this.estadoOperacion = estadoOperacion; }

    public String getActividad() { return actividad; }
    public void setActividad(String actividad) { this.actividad = actividad; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
