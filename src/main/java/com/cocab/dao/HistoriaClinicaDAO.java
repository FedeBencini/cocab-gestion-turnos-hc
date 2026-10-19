package com.cocab.dao;

import java.util.ArrayList;
import java.util.Optional;

import com.cocab.model.historia.EvolucionClinica;
import com.cocab.model.historia.HistoriaClinica;
import com.cocab.model.paciente.Paciente;

/** Historias clínicas, solo para odontólogos activos. Las evoluciones son inmutables y no se borran. */
public interface HistoriaClinicaDAO {
    /** Catálogo clínico, sin precargar historias en los pacientes. */
    ArrayList<Paciente> listarPacientes(boolean incluirInactivos);

    /** Vacío significa paciente existente sin HC; nunca crea una automáticamente. */
    Optional<HistoriaClinica> buscarPorPaciente(int idPaciente);

    /** Si la HC ya existe la devuelve sin modificarla; solo una apertura nueva exige paciente activo. */
    HistoriaClinica abrir(int idPaciente, String grupoSanguineo, String alergias,
                          String antecedentesMedicos);

    /** Reemplaza los datos de la ficha de una HC activa; legajo, apertura y evoluciones no cambian. */
    HistoriaClinica actualizarCabecera(int idPaciente, int idHistoriaClinica,
                                       String grupoSanguineo, String alergias, String antecedentesMedicos);

    /** Anexa una evolución nueva: el autor sale de la sesión y la fecha/hora las fija el sistema. */
    EvolucionClinica anexarEvolucion(int idPaciente, int idHistoriaClinica,
                                     String piezaDental, String diagnostico, String tratamientoRealizado,
                                     String observaciones);
}