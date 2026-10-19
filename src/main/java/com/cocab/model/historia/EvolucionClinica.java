package com.cocab.model.historia;

import java.time.LocalDateTime;

import com.cocab.exception.ValidationException;
import com.cocab.model.odontologo.Odontologo;
import com.cocab.util.Validador;

/** Representa una evolución clínica dentro de la historia de un paciente, incluyendo datos del odontólogo autor, diagnóstico, tratamiento y observaciones. */
public final class EvolucionClinica {
    private int idEvolucion;
    private int idHistoriaClinica;
    private Odontologo odontologo;
    private LocalDateTime fechaHora;
    private String piezaDental;
    private String diagnostico;
    private String tratamientoRealizado;
    private String observaciones;

    public EvolucionClinica() {
    }

    public EvolucionClinica(int idEvolucion, int idHistoriaClinica, Odontologo odontologo,
                            LocalDateTime fechaHora, String piezaDental, String diagnostico,
                            String tratamientoRealizado, String observaciones) {
        setIdHistoriaClinica(idHistoriaClinica);
        setOdontologo(odontologo);
        setFechaHora(fechaHora);
        setPiezaDental(piezaDental);
        setDiagnostico(diagnostico);
        setTratamientoRealizado(tratamientoRealizado);
        setObservaciones(observaciones);
        setIdEvolucion(idEvolucion);
    }

    public EvolucionClinica(int idHistoriaClinica, Odontologo odontologo,
                            LocalDateTime fechaHora, String piezaDental, String diagnostico,
                            String tratamientoRealizado, String observaciones) {
        this(0, idHistoriaClinica, odontologo, fechaHora, piezaDental,
                diagnostico, tratamientoRealizado, observaciones);
    }

    public EvolucionClinica(EvolucionClinica origen) {
        this(Validador.requerirNoNulo(origen, "La evolución").idEvolucion,
                origen.idHistoriaClinica, origen.odontologo, origen.fechaHora,
                origen.piezaDental, origen.diagnostico, origen.tratamientoRealizado,
                origen.observaciones);
    }

    // No necesario: definido en TP2, pero ninguna funcionalidad lo utiliza; Validador ya exige estos campos.
    // public boolean validarCamposClinicos() {
    // }

    /** Indica si la evolución está persistida y, por lo tanto, es inmutable. */
    public boolean asegurarInmutabilidad() {
        return idEvolucion > 0;
    }

    /** Valida que la evolución cumpla con los requisitos para ser registrada. */
    public void validarParaRegistro() {
        validarDatosPersistibles();
        if (!odontologo.estaHabilitado()) {
            throw new ValidationException("El autor de una nueva evolución debe ser un odontólogo activo.");
        }
    }

    /** Valida que los datos de la evolución sean consistentes y persistibles. */
    private void validarDatosPersistibles() {
        Validador.validarIdPersistido(idHistoriaClinica, "El identificador de historia clínica");
        Validador.requerirNoNulo(odontologo, "El odontólogo autor");
        Validador.validarIdPersistido(odontologo.getIdOdontologo(), "El identificador del autor");
        Validador.validarIdPersistido(odontologo.getIdPersona(), "El identificador de persona del autor");
        if (!odontologo.validarDatosPersonales()) {
            throw new ValidationException("Los datos del odontólogo autor están incompletos.");
        }
        Validador.validarFechaHoraSql(fechaHora, "La fecha y hora de la evolución");
        Validador.validarTextoObligatorio(diagnostico, "El diagnóstico");
        Validador.validarTextoObligatorio(tratamientoRealizado, "El tratamiento realizado");
    }

    /** Verifica que la evolución pueda ser editada; lanza excepción si está persistida. */
    private void exigirEditable() {
        if (asegurarInmutabilidad()) {
            throw new ValidationException("Una evolución persistida es inmutable. Registre una nueva evolución.");
        }
    }

    /* Getters y Setters con validaciones */
    public int getIdEvolucion() {
        return idEvolucion;
    }

    /** El DAO debe asignar el ID solo después de confirmar la persistencia. */
    public void setIdEvolucion(int idEvolucion) {
        exigirEditable();
        Validador.validarId(idEvolucion, "El identificador de evolución");
        if (idEvolucion > 0) {
            validarDatosPersistibles();
        }
        this.idEvolucion = idEvolucion;
    }

    public int getIdHistoriaClinica() {
        return idHistoriaClinica;
    }

    public void setIdHistoriaClinica(int idHistoriaClinica) {
        exigirEditable();
        this.idHistoriaClinica = Validador.validarId(idHistoriaClinica, "El identificador de historia clínica");
    }

    public Odontologo getOdontologo() {
        return odontologo == null ? null : new Odontologo(odontologo);
    }

    public void setOdontologo(Odontologo odontologo) {
        exigirEditable();
        this.odontologo = new Odontologo(Validador.requerirNoNulo(odontologo, "El odontólogo autor"));
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        exigirEditable();
        this.fechaHora = Validador.validarFechaHoraSql(fechaHora, "La fecha y hora de la evolución");
    }

    public String getPiezaDental() {
        return piezaDental;
    }

    public void setPiezaDental(String piezaDental) {
        exigirEditable();
        this.piezaDental = Validador.validarOpcional(piezaDental, "La pieza dental", 20);
    }

    public String getDiagnostico() {
        return diagnostico;
    }

    public void setDiagnostico(String diagnostico) {
        exigirEditable();
        this.diagnostico = Validador.validarTextoObligatorio(diagnostico, "El diagnóstico");
    }

    public String getTratamientoRealizado() {
        return tratamientoRealizado;
    }

    public void setTratamientoRealizado(String tratamientoRealizado) {
        exigirEditable();
        this.tratamientoRealizado = Validador.validarTextoObligatorio(tratamientoRealizado, "El tratamiento realizado");
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        exigirEditable();
        this.observaciones = Validador.validarTextoOpcional(observaciones, "Las observaciones");
    }

    @Override
    public String toString() {
        return "EvolucionClinica{idEvolucion=" + idEvolucion + ", persistida=" + asegurarInmutabilidad() + "}";
    }
}