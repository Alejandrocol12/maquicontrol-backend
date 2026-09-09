package com.maquicontrol.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vistas_enlace")
public class VistaEnlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String token;
    private String nombreVisitante;
    private LocalDateTime fecha;
    // Id anonimo guardado en el navegador del visitante (localStorage), para actualizar su
    // fecha de visita en vez de crear una fila nueva cada vez que abre el mismo enlace.
    private String visitanteId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getNombreVisitante() { return nombreVisitante; }
    public void setNombreVisitante(String nombreVisitante) { this.nombreVisitante = nombreVisitante; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public String getVisitanteId() { return visitanteId; }
    public void setVisitanteId(String visitanteId) { this.visitanteId = visitanteId; }
}
