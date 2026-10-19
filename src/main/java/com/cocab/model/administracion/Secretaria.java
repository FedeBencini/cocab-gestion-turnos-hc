package com.cocab.model.administracion;

import com.cocab.model.base.Persona;
import com.cocab.util.Validador;

/** Representa a una secretaria con datos personales y un legajo de empleado. */
public class Secretaria extends Persona {
    private int idSecretaria;
    private String legajoEmpleado;

    public Secretaria() {
        super();
    }

    public Secretaria(int idSecretaria, int idPersona, String dni, String nombre,
                      String apellido, String telefono, String email, String direccion, String legajoEmpleado) {
        super(idPersona, dni, nombre, apellido, telefono, email, direccion);
        setIdSecretaria(idSecretaria);
        setLegajoEmpleado(legajoEmpleado);
    }

    public Secretaria(String dni, String nombre, String apellido, String telefono,
                      String email, String direccion, String legajoEmpleado) {
        this(0, 0, dni, nombre, apellido, telefono, email, direccion, legajoEmpleado);
    }

    /** Valida que el legajo de empleado cumpla con los requisitos de formato. */
    public boolean validarLegajo() {
        return legajoEmpleado != null && !legajoEmpleado.isBlank()
                && legajoEmpleado.codePointCount(0, legajoEmpleado.length()) <= 20;
    }

    /** Valida que los datos de la secretaria sean consistentes y persistibles. */
    @Override
    public boolean validarDatosPersonales() {
        return super.validarDatosPersonales() && validarLegajo();
    }

    /* Getters y Setters con validaciones */
    public int getIdSecretaria() {
        return idSecretaria;
    }

    public void setIdSecretaria(int idSecretaria) {
        this.idSecretaria = Validador.validarId(idSecretaria, "El identificador de secretaria");
    }

    public String getLegajoEmpleado() {
        return legajoEmpleado;
    }

    public void setLegajoEmpleado(String legajoEmpleado) {
        this.legajoEmpleado = Validador.validarObligatorio(legajoEmpleado, "El legajo de empleado", 20);
    }

    @Override
    public String toString() {
        return "Secretaria{idSecretaria=" + idSecretaria + "}";
    }
}