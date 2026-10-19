package com.cocab.dao;

import java.time.LocalDate;
import java.util.ArrayList;

import com.cocab.model.odontologo.Odontologo;
import com.cocab.model.paciente.Paciente;
import com.cocab.model.turno.Turno;

/** Agenda de turnos; sin JavaFX ni historias clínicas. */
public interface TurnoDAO {
    /**
     * Rango inclusivo de uno a siete días. Secretaria puede filtrar por profesional o ver todos;
     * un odontólogo solo ve su propia agenda. Orden: fecha, hora e ID.
     */
    ArrayList<Turno> listar(LocalDate desde, LocalDate hasta, Integer idOdontologo, boolean incluirInactivos);

    /** Catálogo para secretaria. */
    ArrayList<Paciente> listarPacientesActivos();

    /** Catálogo para secretaria; usar false en nuevas asignaciones. */
    ArrayList<Odontologo> listarOdontologos(boolean incluirInactivos);

    /** Registra un turno Asignado, validando que no se superponga con otro del profesional. */
    Turno registrar(Turno nuevo);

    /** Reprograma un turno Asignado/Reprogramado (queda Reprogramado), validando la superposición. */
    Turno reprogramar(Turno original, Turno cambios);

    /** Cancela un turno Asignado/Reprogramado, sin borrarlo. */
    void cancelar(Turno original);
}