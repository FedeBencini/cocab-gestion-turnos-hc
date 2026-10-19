package com.cocab.model.seguridad;

/** Representa los posibles roles de un usuario en el sistema. */
public enum RolUsuario {
    Secretaria("Secretaria"),
    Odontologo("Odontologo");

    private final String descripcion;

    RolUsuario(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}