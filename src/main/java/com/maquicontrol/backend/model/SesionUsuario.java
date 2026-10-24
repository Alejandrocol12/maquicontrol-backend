package com.maquicontrol.backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Una sesión abierta de un usuario en un dispositivo. El token lleva su id; al revocarla, el token deja de servir.
@Entity
@Table(name = "sesiones_usuario", indexes = @Index(name = "idx_sesion_usuario", columnList = "usuarioId"))
public class SesionUsuario {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private Long usuarioId;

    private String dispositivo;

    private LocalDateTime creada;

    private LocalDateTime ultimaActividad;

    private boolean revocada = false;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getDispositivo() { return dispositivo; }
    public void setDispositivo(String dispositivo) { this.dispositivo = dispositivo; }

    public LocalDateTime getCreada() { return creada; }
    public void setCreada(LocalDateTime creada) { this.creada = creada; }

    public LocalDateTime getUltimaActividad() { return ultimaActividad; }
    public void setUltimaActividad(LocalDateTime ultimaActividad) { this.ultimaActividad = ultimaActividad; }

    public boolean isRevocada() { return revocada; }
    public void setRevocada(boolean revocada) { this.revocada = revocada; }
}
