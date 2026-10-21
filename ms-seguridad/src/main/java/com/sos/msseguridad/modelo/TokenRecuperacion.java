package com.sos.msseguridad.modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Token de un solo uso para activar una cuenta, recuperar la clave o
 * confirmar un cambio de correo. Solo se guarda el SHA-256 del token.
 */
@Entity
@Table(name = "token_recuperacion")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenRecuperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token")
    private Integer idToken;

    @ManyToOne
    @JoinColumn(name = "id_personal", nullable = false)
    private Personal personal;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_expira", nullable = false)
    private LocalDateTime fechaExpira;

    @Column(name = "usado", nullable = false)
    private Boolean usado = false;

    @Column(name = "ip_solicitud", length = 45)
    private String ipSolicitud;

    @Column(name = "user_agent", length = 200)
    private String userAgent;
}
