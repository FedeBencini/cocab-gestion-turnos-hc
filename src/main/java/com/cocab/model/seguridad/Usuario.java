package com.cocab.model.seguridad;

import java.util.Arrays;

import com.cocab.exception.ValidationException;
import com.cocab.util.SeguridadClaves;
import com.cocab.util.Validador;

/** Representa una cuenta de autenticación de usuario. No debe usarse como contenedor de la sesión activa. */
public class Usuario {
    private int idUsuario;
    private int idPersona;
    private String nombreUsuario;
    private String clave;
    private RolUsuario rol;
    private boolean estadoActivo = true;

    public Usuario() {
    }

    public Usuario(int idUsuario, int idPersona, String nombreUsuario, String clave,
                   RolUsuario rol, boolean estadoActivo) {
        setIdUsuario(idUsuario);
        setIdPersona(idPersona);
        setNombreUsuario(nombreUsuario);
        setClave(clave);
        setRol(rol);
        setEstadoActivo(estadoActivo);
    }

    /** Sin ID de usuario; conserva la referencia a la persona asociada. */
    public Usuario(int idPersona, String nombreUsuario, String clave, RolUsuario rol) {
        this(0, idPersona, nombreUsuario, clave, rol, true);
    }

    /** Verifica si las credenciales proporcionadas coinciden con las almacenadas. */
    public boolean verificarCredenciales(String claveIngresada) {
        if (!estadoActivo || claveIngresada == null || clave == null) {
            return false;
        }
        char[] caracteres = claveIngresada.toCharArray();
        try {
            return SeguridadClaves.verificarClave(caracteres, clave);
        } finally {
            Arrays.fill(caracteres, '\0');
        }
    }

    /** Verifica si el usuario tiene el rol especificado. */
    public boolean tieneRol(RolUsuario rolRequerido) {
        return rolRequerido != null && rol == rolRequerido;
    }

    // No necesario: definido en TP2, pero ninguna funcionalidad lo utiliza; las cuentas se gestionan fuera de la GUI.
    //public void inhabilitarAcceso() {
    //}

    /* Getters y Setters con validaciones */
    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = Validador.validarId(idUsuario, "El identificador de usuario");
    }

    public int getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(int idPersona) {
        this.idPersona = Validador.validarId(idPersona, "El identificador de persona");
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = Validador.validarObligatorio(nombreUsuario, "El nombre de usuario", 50);
    }

    /** Solo para almacenamiento y verificación; nunca incluir este valor en sesión o logs. */
    public String getClave() {
        return clave;
    }

    /** Recibe exclusivamente el hash PBKDF2 serializado, nunca una clave en texto plano. */
    public void setClave(String clave) {
        Validador.validarObligatorio(clave, "El hash de la clave", 255);
        if (!SeguridadClaves.esFormatoValido(clave)) {
            throw new ValidationException("El hash de la clave no tiene un formato PBKDF2 válido.");
        }
        this.clave = clave;
    }

    public RolUsuario getRol() {
        return rol;
    }

    public void setRol(RolUsuario rol) {
        this.rol = Validador.requerirNoNulo(rol, "El rol de usuario");
    }

    public boolean isEstadoActivo() {
        return estadoActivo;
    }

    public void setEstadoActivo(boolean estadoActivo) {
        this.estadoActivo = estadoActivo;
    }

    @Override
    public String toString() {
        return "Usuario{idUsuario=" + idUsuario + ", rol=" + rol + ", estadoActivo=" + estadoActivo + "}";
    }
}