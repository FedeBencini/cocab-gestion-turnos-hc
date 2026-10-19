package com.cocab.dao;

import java.util.ArrayList;

import com.cocab.model.paciente.Paciente;

/** Padrón administrativo exclusivo de secretaria; nunca carga contenido clínico. */
public interface PacienteDAO {
    ArrayList<Paciente> listar(boolean incluirInactivos);

    Paciente registrar(Paciente nuevo);

    /** Modifica los datos de un paciente existente, comparando con la instantánea original. */
    Paciente modificar(Paciente original, Paciente cambios);

    void darDeBaja(Paciente original);
}