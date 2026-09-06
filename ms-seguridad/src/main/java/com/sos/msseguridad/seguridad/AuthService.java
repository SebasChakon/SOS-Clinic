/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sos.msseguridad.seguridad;

import com.sos.msseguridad.dto.LoginRequest;
import com.sos.msseguridad.dto.LoginResponse;
import com.sos.msseguridad.modelo.Personal;
import com.sos.msseguridad.repositorio.PersonalRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final PersonalRepository personalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(PersonalRepository personalRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.personalRepository = personalRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Personal personal = personalRepository.findByUsuario(request.getUsuario())
                .orElseThrow(() -> new BadCredentialsException("Usuario o clave incorrectos"));

        if (personal.getIdEstado() == 0) {
            throw new BadCredentialsException("Usuario inactivo");
        }

        if (!passwordEncoder.matches(request.getClave(), personal.getClave())) {
            throw new BadCredentialsException("Usuario o clave incorrectos");
        }

        String token = jwtService.generarToken(
                personal.getUsuario(),
                personal.getIdPersonal(),
                personal.getPerfil().getPerfil()
        );

        return new LoginResponse(token, personal.getUsuario(), personal.getPerfil().getPerfil(), personal.getIdPersonal());
    }
}
