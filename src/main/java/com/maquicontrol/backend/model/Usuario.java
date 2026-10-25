package com.maquicontrol.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String empresa;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String rol = "ADMIN";

    @Column(nullable = false)
    private boolean activo = true;

    private Long operadorId;

    private String pin;

    // Telegram del dueño: recibe avisos (por ejemplo, lecturas del horómetro en revisión) y las aprueba desde el chat
    private Long telegramChatId;
    private String telegramLinkCode;
    private java.time.LocalDateTime telegramLinkExpira;

    // Datos de la empresa que salen en el encabezado de los PDF
    private String nit;
    private String telefono;
    private String ciudad;
    private String direccion;

    @Column(columnDefinition = "LONGTEXT")
    private String logoEmpresa;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public Long getOperadorId() { return operadorId; }
    public void setOperadorId(Long operadorId) { this.operadorId = operadorId; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getLogoEmpresa() { return logoEmpresa; }
    public void setLogoEmpresa(String logoEmpresa) { this.logoEmpresa = logoEmpresa; }

    public Long getTelegramChatId() { return telegramChatId; }
    public void setTelegramChatId(Long telegramChatId) { this.telegramChatId = telegramChatId; }

    public String getTelegramLinkCode() { return telegramLinkCode; }
    public void setTelegramLinkCode(String telegramLinkCode) { this.telegramLinkCode = telegramLinkCode; }

    public java.time.LocalDateTime getTelegramLinkExpira() { return telegramLinkExpira; }
    public void setTelegramLinkExpira(java.time.LocalDateTime telegramLinkExpira) { this.telegramLinkExpira = telegramLinkExpira; }
}
