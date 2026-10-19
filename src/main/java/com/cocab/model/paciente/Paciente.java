package com.cocab.model.paciente;

import java.time.LocalDate;
import java.time.Period;

import com.cocab.exception.ValidationException;
import com.cocab.model.base.Persona;
import com.cocab.model.historia.HistoriaClinica;
import com.cocab.util.Validador;

/** Representa un paciente en el sistema, incluyendo sus datos personales y clínicos. */
public class Paciente extends Persona {
    private int idPaciente;
    private LocalDate fechaNacimiento;
    private String obraSocial = "Particular";
    private boolean estadoActivo = true;
    private HistoriaClinica historiaClinica;

    public Paciente() {
        super();
    }

    public Paciente(int idPaciente, int idPersona, String dni, String nombre,
                    String apellido, String telefono, String email, String direccion,
                    LocalDate fechaNacimiento, String obraSocial, boolean estadoActivo,
                    HistoriaClinica historiaClinica) {
        super(idPersona, dni, nombre, apellido, telefono, email, direccion);
        setIdPaciente(idPaciente);
        setFechaNacimiento(fechaNacimiento);
        setObraSocial(obraSocial);
        setEstadoActivo(estadoActivo);
        setHistoriaClinica(historiaClinica);
    }

    public Paciente(int idPaciente, int idPersona, String dni, String nombre,
                    String apellido, String telefono, String email, String direccion,
                    LocalDate fechaNacimiento, String obraSocial, boolean estadoActivo) {
        this(idPaciente, idPersona, dni, nombre, apellido, telefono, email, direccion,
                fechaNacimiento, obraSocial, estadoActivo, null);
    }

    public Paciente(String dni, String nombre, String apellido, String telefono,
                    String email, String direccion, LocalDate fechaNacimiento, String obraSocial) {
        this(0, 0, dni, nombre, apellido, telefono, email, direccion,
                fechaNacimiento, obraSocial, true, null);
    }

    /** Valida que los datos personales y clínicos del paciente sean correctos. */
    @Override
    public boolean validarDatosPersonales() {
        return super.validarDatosPersonales() && fechaNacimiento != null
                && fechaNacimiento.isBefore(LocalDate.now());
    }

    /** Calcula la edad del paciente en años completos. */
    public int calcularEdad() {
        Validador.validarFechaNacimiento(fechaNacimiento);
        return Period.between(fechaNacimiento, LocalDate.now()).getYears();
    }

    /** Verifica si el paciente está habilitado (activo) en el sistema. */
    public boolean estaHabilitado() {
        return estadoActivo;
    }

    /** Registra la baja del paciente, desactivando su estado en el sistema. */
    public void registrarBaja() {
        estadoActivo = false;
    }

    /* Getters y Setters con validaciones */
    public int getIdPaciente() {
        return idPaciente;
    }

    public void setIdPaciente(int idPaciente) {
        Validador.validarId(idPaciente, "El identificador de paciente");
        if (historiaClinica != null && historiaClinica.getIdPaciente() != idPaciente) {
            throw new ValidationException("El identificador no coincide con el paciente de la historia clínica.");
        }
        this.idPaciente = idPaciente;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = Validador.validarFechaNacimiento(fechaNacimiento);
    }

    public String getObraSocial() {
        return obraSocial;
    }

    public void setObraSocial(String obraSocial) {
        this.obraSocial = Validador.validarOpcional(obraSocial, "La obra social", 50);
    }

    public boolean isEstadoActivo() {
        return estadoActivo;
    }

    public void setEstadoActivo(boolean estadoActivo) {
        this.estadoActivo = estadoActivo;
    }

    // No necesario: definido en TP2, pero ninguna funcionalidad lee la HC desde el paciente; el padrón no carga contenido clínico.
    // public HistoriaClinica getHistoriaClinica() {
    // }

    public void setHistoriaClinica(HistoriaClinica historiaClinica) {
        if (historiaClinica != null && historiaClinica.getIdPaciente() != idPaciente) {
            throw new ValidationException("La historia clínica debe pertenecer al paciente.");
        }
        this.historiaClinica = historiaClinica;
    }

    @Override
    public String toString() {
        return "Paciente{idPaciente=" + idPaciente + ", estadoActivo=" + estadoActivo + "}";
    }
}