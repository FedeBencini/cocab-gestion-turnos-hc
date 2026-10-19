package com.cocab.dao;

import java.util.Optional;

import com.cocab.model.seguridad.Usuario;

/** Solo lectura de usuarios; no gestiona aprovisionamiento ni cambios de credenciales. */
public interface UsuarioDAO {
    /** Modelo temporal: extraer identidad mínima y no retenerlo en la sesión. */
    Optional<Usuario> autenticar(String nombreUsuario, String claveIngresada);

    // No necesario: definido en TP2, pero ninguna funcionalidad lo utiliza; agenda e historias clínicas resuelven al profesional por su cuenta.
    // OptionalInt buscarIdOdontologoActivo(int idPersona);
}