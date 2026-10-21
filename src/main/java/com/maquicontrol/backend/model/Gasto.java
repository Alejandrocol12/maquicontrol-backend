package com.maquicontrol.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "gastos")
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;

    private String descripcion;
    private String categoria;
    private double monto;
    private LocalDate fecha;
    private String maquinaNombre;
    private Long faenaId;

    // Quién pagó el gasto (nombre de la lista de pagadores). Vacío = sin asignar.
    @Column(length = 80)
    private String pagadoPor;

    // Proveedor y número de factura o remisión (opcionales)
    @Column(length = 120)
    private String proveedor;

    @Column(length = 60)
    private String numeroFactura;

    @JsonIgnore
    @Column(name = "factura_pdf", columnDefinition = "LONGBLOB")
    private byte[] facturaPdf;

    @Column(name = "factura_nombre")
    private String facturaNombre;

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public String getMaquinaNombre() { return maquinaNombre; }
    public void setMaquinaNombre(String maquinaNombre) { this.maquinaNombre = maquinaNombre; }

    public String getProveedor() { return proveedor; }
    public void setProveedor(String proveedor) { this.proveedor = proveedor; }

    public String getNumeroFactura() { return numeroFactura; }
    public void setNumeroFactura(String numeroFactura) { this.numeroFactura = numeroFactura; }

    public byte[] getFacturaPdf() { return facturaPdf; }
    public void setFacturaPdf(byte[] facturaPdf) { this.facturaPdf = facturaPdf; }

    public String getFacturaNombre() { return facturaNombre; }
    public void setFacturaNombre(String facturaNombre) { this.facturaNombre = facturaNombre; }

    public boolean isTieneFactura() { return facturaPdf != null && facturaPdf.length > 0; }

    public Long getFaenaId() { return faenaId; }
    public void setFaenaId(Long faenaId) { this.faenaId = faenaId; }

    public String getPagadoPor() { return pagadoPor; }
    public void setPagadoPor(String pagadoPor) { this.pagadoPor = pagadoPor; }
}
