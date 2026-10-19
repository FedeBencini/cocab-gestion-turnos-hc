package com.cocab.model.base;

import com.cocab.util.Validador;

/** Representa una persona con datos filiatorios básicos, incluyendo identificación, nombre, apellido, contacto y dirección. */
public abstract class Persona {
    private int idPersona;
    private String dni;
    private String nombre;
    private String apellido;
    private String telefono;
    private String email;
    private String direccion;

    public Persona() {
    }

    public Persona(int idPersona, String dni, String nombre, String apellido,
                   String telefono, String email, String direccion) {
        setIdPersona(idPersona);
        setDni(dni);
        setNombre(nombre);
        setApellido(apellido);
        setTelefono(telefono);
        setEmail(email);
        setDireccion(direccion);
    }

    public Persona(String dni, String nombre, String apellido, String telefono,
                   String email, String direccion) {
        this(0, dni, nombre, apellido, telefono, email, direccion);
    }

    /** Valida que el DNI cumpla con el formato esperado. */
    public static boolean validarDni(String dni) {
        return Validador.esDniValido(dni);
    }

    /** Valida que los datos personales obligatorios de la persona estén completos. */
    public boolean validarDatosPersonales() {
        return validarDni(dni) && nombre != null && !nombre.isBlank()
                && apellido != null && !apellido.isBlank();
    }

    /** Obtiene el nombre completo de la persona en el formato "Apellido, Nombre". */
    public String obtenerNombreCompleto() {
        return (apellido == null ? "" : apellido) + ", " + (nombre == null ? "" : nombre);
    }

    /* Getters y Setters con validaciones */
    public int getIdPersona() {
        return idPersona;
    }

    public void setIdPersona(int idPersona) {
        this.idPersona = Validador.validarId(idPersona, "El identificador de persona");
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = Validador.validarDni(dni);
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = Validador.validarObligatorio(nombre, "El nombre", 50);
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = Validador.validarObligatorio(apellido, "El apellido", 50);
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = Validador.validarOpcional(telefono, "El teléfono", 20);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = Validador.validarOpcional(email, "El correo electrónico", 100);
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = Validador.validarOpcional(direccion, "La dirección", 150);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{idPersona=" + idPersona + "}";
    }
}