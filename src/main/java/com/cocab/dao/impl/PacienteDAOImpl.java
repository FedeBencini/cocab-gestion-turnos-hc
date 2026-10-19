package com.cocab.dao.impl;

import java.util.ArrayList;

import com.cocab.dao.PacienteDAO;
import com.cocab.exception.ValidationException;
import com.cocab.mock.MockDatabase;
import com.cocab.model.paciente.Paciente;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.util.UserSession;
import com.cocab.util.Validador;

/**
 * Implementación de {@link PacienteDAO} sobre el repositorio en memoria {@link MockDatabase},
 * con las reglas de validación y autorización del dominio.
 * Al depender solo de la interfaz, la próxima entrega incorporará una base de datos sin modificar los controladores.
 */
public final class PacienteDAOImpl implements PacienteDAO {

    /** Lista los pacientes, incluyendo o excluyendo los inactivos según el parámetro. */
    @Override
    public ArrayList<Paciente> listar(boolean incluirInactivos) {
        exigirSesion();
        return MockDatabase.getInstance().listarPacientes(incluirInactivos);
    }

    /** Registra un nuevo paciente en el sistema. */
    @Override
    public Paciente registrar(Paciente nuevo) {
        exigirSesion();
        Paciente copia = copiar(nuevo, true);
        return MockDatabase.getInstance().registrarPaciente(copia);
    }

    /** Modifica los datos de un paciente existente. */
    @Override
    public Paciente modificar(Paciente original, Paciente cambios) {
        exigirSesion();
        Paciente esperado = copiar(original, false);
        Paciente propuesto = copiar(cambios, false);
        exigirMismaIdentidadYActividad(esperado, propuesto);
        return MockDatabase.getInstance().modificarPaciente(esperado, propuesto);
    }

    /** Da de baja a un paciente, dejándolo inactivo en el sistema. */
    @Override
    public void darDeBaja(Paciente original) {
        exigirSesion();
        Paciente esperado = copiar(original, false);
        MockDatabase.getInstance().darDeBajaPaciente(esperado);
    }

    /** Exige que la sesión esté activa y que el usuario tenga el rol de secretaria. */
    private void exigirSesion() {
        UserSession.getInstance().exigirRol(RolUsuario.Secretaria);
    }

    /** Crea una copia de un paciente, validando los campos según si es un alta o una modificación. */
    private static Paciente copiar(Paciente origen, boolean alta) {
        Validador.requerirNoNulo(origen, "El paciente");
        // Valida primero IDs de especialización y persona, incluso con constructor vacío de UI.
        int idPaciente = origen.getIdPaciente();
        int idPersona = origen.getIdPersona();
        boolean activo = origen.isEstadoActivo();
        if (alta) {
            if (idPaciente != 0 || idPersona != 0 || !activo) {
                throw new ValidationException("Un paciente nuevo debe tener identificadores cero y estar activo.");
            }
        } else {
            Validador.validarIdPersistido(idPaciente, "El identificador de paciente");
            Validador.validarIdPersistido(idPersona, "El identificador de persona");
        }
        // No lee ni copia historiaClinica: la instantánea es exclusivamente administrativa.
        return new Paciente(idPaciente, idPersona, origen.getDni(), origen.getNombre(), origen.getApellido(),
                origen.getTelefono(), origen.getEmail(), origen.getDireccion(), origen.getFechaNacimiento(),
                origen.getObraSocial(), activo);
    }

    /** Exige que la identidad y el estado de actividad de un paciente no cambien durante la modificación. */
    private static void exigirMismaIdentidadYActividad(Paciente original, Paciente cambios) {
        if (original.getIdPaciente() != cambios.getIdPaciente()
                || original.getIdPersona() != cambios.getIdPersona()
                || original.isEstadoActivo() != cambios.isEstadoActivo()) {
            throw new ValidationException("La modificación no permite cambiar identificadores ni actividad del paciente.");
        }
    }
}