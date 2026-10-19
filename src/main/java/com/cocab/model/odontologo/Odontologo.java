package com.cocab.model.odontologo;

import com.cocab.model.base.Persona;
import com.cocab.util.Validador;

/** Representa un odontólogo en el sistema, incluyendo sus datos personales y profesionales. */
public class Odontologo extends Persona {
    private int idOdontologo;
    private String matricula;
    private String especialidad;
    private boolean estadoActivo = true;

    public Odontologo() {
        super();
    }

    public Odontologo(int idOdontologo, int idPersona, String dni, String nombre,
                      String apellido, String telefono, String email, String direccion,
                      String matricula, String especialidad, boolean estadoActivo) {
        super(idPersona, dni, nombre, apellido, telefono, email, direccion);
        setIdOdontologo(idOdontologo);
        setMatricula(matricula);
        setEspecialidad(especialidad);
        setEstadoActivo(estadoActivo);
    }

    public Odontologo(String dni, String nombre, String apellido, String telefono,
                      String email, String direccion, String matricula, String especialidad) {
        this(0, 0, dni, nombre, apellido, telefono, email, direccion, matricula, especialidad, true);
    }

    public Odontologo(Odontologo origen) {
        this(Validador.requerirNoNulo(origen, "El odontólogo").getIdOdontologo(),
                origen.getIdPersona(), origen.getDni(), origen.getNombre(), origen.getApellido(),
                origen.getTelefono(), origen.getEmail(), origen.getDireccion(),
                origen.getMatricula(), origen.getEspecialidad(), origen.isEstadoActivo());
    }

    /** Verifica si el odontólogo está habilitado (activo) en el sistema. */
    public boolean estaHabilitado() {
        return estadoActivo;
    }

    /** Valida que la matrícula del odontólogo cumpla con los requisitos del sistema. */
    public boolean validarMatricula() {
        return matricula != null && !matricula.isBlank()
                && matricula.codePointCount(0, matricula.length()) <= 20;
    }

    /** Valida que los datos personales y profesionales del odontólogo sean correctos. */
    @Override
    public boolean validarDatosPersonales() {
        return super.validarDatosPersonales() && validarMatricula()
                && especialidad != null && !especialidad.isBlank();
    }

    /** Registra la baja del odontólogo, desactivando su estado en el sistema. */
    public void registrarBaja() {
        estadoActivo = false;
    }

    /* Getters y Setters con validaciones */
    public int getIdOdontologo() {
        return idOdontologo;
    }

    public void setIdOdontologo(int idOdontologo) {
        this.idOdontologo = Validador.validarId(idOdontologo, "El identificador de odontólogo");
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = Validador.validarObligatorio(matricula, "La matrícula", 20);
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = Validador.validarObligatorio(especialidad, "La especialidad", 100);
    }

    public boolean isEstadoActivo() {
        return estadoActivo;
    }

    public void setEstadoActivo(boolean estadoActivo) {
        this.estadoActivo = estadoActivo;
    }

    @Override
    public String toString() {
        return "Odontologo{idOdontologo=" + idOdontologo + ", estadoActivo=" + estadoActivo + "}";
    }
}