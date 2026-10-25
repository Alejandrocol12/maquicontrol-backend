package com.maquicontrol.backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Una lectura del horómetro enviada por el operador con una foto, desde que llega hasta que se registra o se descarta.
// Estados: ELEGIR_MAQUINA, CONFIRMAR, ESPERA_NUMERO (esperan al operador), REVISION (espera al dueño),
// REGISTRADA, RECHAZADA, CANCELADA.
@Entity
@Table(name = "lecturas_horometro", indexes = {
        @Index(name = "idx_lecturas_horometro_usuario", columnList = "usuarioId"),
        @Index(name = "idx_lecturas_horometro_operador", columnList = "operadorId")
})
public class LecturaHorometro {

    public static final String ELEGIR_MAQUINA = "ELEGIR_MAQUINA";
    public static final String CONFIRMAR = "CONFIRMAR";
    public static final String ESPERA_NUMERO = "ESPERA_NUMERO";
    public static final String REVISION = "REVISION";
    public static final String REGISTRADA = "REGISTRADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String CANCELADA = "CANCELADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long usuarioId;

    private Long operadorId;
    private String operadorNombre;
    private Long chatId;

    private Long maquinaId;
    private String maquinaNombre;

    // Lo que leyó la IA (o lo que escribió el operador) y qué tan segura estaba
    private Double lectura;
    private Integer confianza;
    private Double anterior;
    private Double horas;

    @Column(nullable = false)
    private String estado;

    // El operador confirmó que la lectura es correcta aunque la IA dudara o superara el máximo de horas
    private boolean confirmada;
    // El número lo escribió el operador, no salió de la foto
    private boolean corregida;

    private String fotoFileId;

    // Foto reducida. Se borra pasado el tiempo de retención; el registro queda.
    @Column(columnDefinition = "LONGBLOB")
    private byte[] foto;

    private Long horaTrabajadaId;

    private LocalDateTime creada;
    private LocalDateTime resuelta;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public Long getOperadorId() { return operadorId; }
    public void setOperadorId(Long operadorId) { this.operadorId = operadorId; }

    public String getOperadorNombre() { return operadorNombre; }
    public void setOperadorNombre(String operadorNombre) { this.operadorNombre = operadorNombre; }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public Long getMaquinaId() { return maquinaId; }
    public void setMaquinaId(Long maquinaId) { this.maquinaId = maquinaId; }

    public String getMaquinaNombre() { return maquinaNombre; }
    public void setMaquinaNombre(String maquinaNombre) { this.maquinaNombre = maquinaNombre; }

    public Double getLectura() { return lectura; }
    public void setLectura(Double lectura) { this.lectura = lectura; }

    public Integer getConfianza() { return confianza; }
    public void setConfianza(Integer confianza) { this.confianza = confianza; }

    public Double getAnterior() { return anterior; }
    public void setAnterior(Double anterior) { this.anterior = anterior; }

    public Double getHoras() { return horas; }
    public void setHoras(Double horas) { this.horas = horas; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public boolean isConfirmada() { return confirmada; }
    public void setConfirmada(boolean confirmada) { this.confirmada = confirmada; }

    public boolean isCorregida() { return corregida; }
    public void setCorregida(boolean corregida) { this.corregida = corregida; }

    public String getFotoFileId() { return fotoFileId; }
    public void setFotoFileId(String fotoFileId) { this.fotoFileId = fotoFileId; }

    public byte[] getFoto() { return foto; }
    public void setFoto(byte[] foto) { this.foto = foto; }

    public Long getHoraTrabajadaId() { return horaTrabajadaId; }
    public void setHoraTrabajadaId(Long horaTrabajadaId) { this.horaTrabajadaId = horaTrabajadaId; }

    public LocalDateTime getCreada() { return creada; }
    public void setCreada(LocalDateTime creada) { this.creada = creada; }

    public LocalDateTime getResuelta() { return resuelta; }
    public void setResuelta(LocalDateTime resuelta) { this.resuelta = resuelta; }
}
