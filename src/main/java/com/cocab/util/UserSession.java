package com.cocab.util;

import java.util.Optional;

import com.cocab.exception.ValidationException;
import com.cocab.model.seguridad.RolUsuario;

/** Gestión de la sesión del usuario autenticado en memoria. */
public final class UserSession {
    private static final UserSession INSTANCIA = new UserSession();
    private Identidad identidad;

    private UserSession() {
    }

    /** Devuelve la instancia única de la sesión de usuario. */
    public static UserSession getInstance() {
        return INSTANCIA;
    }

    /** Devuelve la identidad del usuario autenticado, que es inmutable y nunca contiene Usuario, contraseña o hash. */
    public synchronized Optional<Identidad> obtenerIdentidad() {
        return Optional.ofNullable(identidad);
    }

    /** Inicia la sesión con la nueva identidad; invocar únicamente después de verificar las credenciales y actividad. */
    public synchronized boolean iniciar(Identidad nuevaIdentidad) {
        Validador.requerirNoNulo(nuevaIdentidad, "La identidad autenticada");
        if (identidad != null) {
            return false;
        }
        identidad = nuevaIdentidad;
        return true;
    }

    /** Indica si hay una sesión de usuario activa. */
    public synchronized boolean haySesion() {
        return identidad != null;
    }

    /** Exige que la sesión tenga uno de los roles permitidos; lanza ValidationException si no se cumple. */
    public synchronized Identidad exigirRol(RolUsuario... permitidos) {
        if (identidad != null && permitidos != null) {
            for (RolUsuario permitido : permitidos) {
                if (identidad.getRol() == permitido) {
                    return identidad;
                }
            }
        }
        throw new ValidationException("La sesión no está vigente o no permite esta operación.");
    }

    /** Cierra la sesión de usuario activa. */
    public synchronized void cerrarSesion() {
        identidad = null;
    }

    /** Representa la identidad mínima del usuario autenticado. */
    public static final class Identidad {
        private final int idUsuario;
        private final int idPersona;
        private final String nombreUsuario;
        private final RolUsuario rol;

        public Identidad(int idUsuario, int idPersona, String nombreUsuario, RolUsuario rol) {
            this.idUsuario = Validador.validarIdPersistido(idUsuario, "El identificador de usuario");
            this.idPersona = Validador.validarIdPersistido(idPersona, "El identificador de persona");
            this.nombreUsuario = Validador.validarObligatorio(nombreUsuario, "El nombre de usuario", 50);
            this.rol = Validador.requerirNoNulo(rol, "El rol de usuario");
        }

        public int getIdUsuario() {
            return idUsuario;
        }

        public int getIdPersona() {
            return idPersona;
        }

        public String getNombreUsuario() {
            return nombreUsuario;
        }

        public RolUsuario getRol() {
            return rol;
        }
    }
}