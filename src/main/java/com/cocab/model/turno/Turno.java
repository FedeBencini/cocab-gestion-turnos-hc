package com.cocab.model.turno;

import java.time.LocalDate;
import java.time.LocalTime;

import com.cocab.exception.ValidationException;
import com.cocab.model.odontologo.Odontologo;
import com.cocab.model.paciente.Paciente;
import com.cocab.util.Validador;

/** Representa un turno odontológico, incluyendo paciente, odontólogo, fecha, hora y estado. */
public class Turno {
    private int idTurno;
    private Paciente paciente;
    private Odontologo odontologo;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String motivoConsulta;
    private EstadoTurno estado = EstadoTurno.Asignado;

    public Turno() {
    }

    public Turno(int idTurno, Paciente paciente, Odontologo odontologo, LocalDate fecha,
                 LocalTime horaInicio, LocalTime horaFin, String motivoConsulta, EstadoTurno estado) {
        setIdTurno(idTurno);
        setPaciente(paciente);
        setOdontologo(odontologo);
        setFecha(fecha);
        setHoraInicio(horaInicio);
        setHoraFin(horaFin);
        setMotivoConsulta(motivoConsulta);
        setEstado(estado);
    }

    public Turno(Paciente paciente, Odontologo odontologo, LocalDate fecha,
                 LocalTime horaInicio, LocalTime horaFin, String motivoConsulta) {
        this(0, paciente, odontologo, fecha, horaInicio, horaFin, motivoConsulta, EstadoTurno.Asignado);
    }

    /** Valida que el turno cumpla con las condiciones necesarias antes de ser asignado. */
    public void validarParaAsignacion() {
        validarAsignacion(fecha, horaInicio, horaFin);
        Validador.requerirNoNulo(estado, "El estado del turno");
    }

    /** Valida que la asignación de un turno cumpla con las condiciones necesarias. */
    private void validarAsignacion(LocalDate fechaPropuesta, LocalTime inicioPropuesto,
                                   LocalTime finPropuesto) {
        Validador.requerirNoNulo(paciente, "El paciente");
        Validador.requerirNoNulo(odontologo, "El odontólogo");
        if (!paciente.estaHabilitado() || !odontologo.estaHabilitado()) {
            throw new ValidationException("El paciente y el odontólogo deben estar activos para asignar un turno.");
        }
        Validador.validarFechaAsignacion(fechaPropuesta);
        Validador.validarFranjaHoraria(inicioPropuesto, finPropuesto);
        Validador.validarObligatorio(motivoConsulta, "El motivo de consulta", 255);
    }

    /** Valida si un nuevo intervalo de tiempo se superpone con el turno actual, considerando el estado. */
    public boolean validarSuperposicion(LocalTime nuevoInicio, LocalTime nuevoFin) {
        Validador.validarIntervalo(horaInicio, horaFin);
        Validador.validarIntervalo(nuevoInicio, nuevoFin);
        return estado != EstadoTurno.Cancelado
                && horaInicio.isBefore(nuevoFin) && horaFin.isAfter(nuevoInicio);
    }

    // No necesario: definido en TP2, pero ninguna funcionalidad lo utiliza; el DAO fija el estado Reprogramado.
    //public void reprogramar(LocalDate nuevaFecha, LocalTime nuevoInicio, LocalTime nuevoFin) {
    //}

    /** Cancela el turno, cambiando su estado a Cancelado. */
    public void cancelar() {
        estado = EstadoTurno.Cancelado;
    }

    // No necesario: definido en TP2, pero ninguna funcionalidad lo utiliza.
    // public boolean estaPendiente() {
    // }

    /* Getters y Setters con validaciones */
    public int getIdTurno() {
        return idTurno;
    }

    public void setIdTurno(int idTurno) {
        this.idTurno = Validador.validarId(idTurno, "El identificador de turno");
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public void setPaciente(Paciente paciente) {
        this.paciente = Validador.requerirNoNulo(paciente, "El paciente");
    }

    public Odontologo getOdontologo() {
        return odontologo;
    }

    public void setOdontologo(Odontologo odontologo) {
        this.odontologo = Validador.requerirNoNulo(odontologo, "El odontólogo");
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = Validador.validarFechaSql(fecha, "La fecha del turno");
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        Validador.validarHoraSql(horaInicio, "La hora de inicio");
        if (horaFin != null) {
            Validador.validarIntervalo(horaInicio, horaFin);
        }
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        Validador.validarHoraSql(horaFin, "La hora de fin");
        if (horaInicio != null) {
            Validador.validarIntervalo(horaInicio, horaFin);
        }
        this.horaFin = horaFin;
    }

    public String getMotivoConsulta() {
        return motivoConsulta;
    }

    public void setMotivoConsulta(String motivoConsulta) {
        this.motivoConsulta = Validador.validarObligatorio(motivoConsulta, "El motivo de consulta", 255);
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public void setEstado(EstadoTurno estado) {
        this.estado = Validador.requerirNoNulo(estado, "El estado del turno");
    }

    @Override
    public String toString() {
        return "Turno{idTurno=" + idTurno + ", estado=" + estado + "}";
    }
}