package com.cocab.dao.impl;

import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalInt;

import com.cocab.dao.HistoriaClinicaDAO;
import com.cocab.exception.ValidationException;
import com.cocab.mock.MockDatabase;
import com.cocab.model.historia.EvolucionClinica;
import com.cocab.model.historia.HistoriaClinica;
import com.cocab.model.odontologo.Odontologo;
import com.cocab.model.paciente.Paciente;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.util.UserSession;
import com.cocab.util.Validador;

/**
 * Implementación de {@link HistoriaClinicaDAO} sobre el repositorio en memoria {@link MockDatabase},
 * exclusiva de odontólogos con sesión vigente.
 * Al depender solo de la interfaz, la próxima entrega incorporará una base de datos sin modificar los controladores.
 */
public final class HistoriaClinicaDAOImpl implements HistoriaClinicaDAO {

    /** Lista los pacientes, incluyendo o excluyendo los inactivos según el parámetro. */
    @Override
    public ArrayList<Paciente> listarPacientes(boolean incluirInactivos) {
        exigirSesion();
        return MockDatabase.getInstance().listarPacientes(incluirInactivos);
    }

    /** Busca la historia clínica asociada a un paciente, si existe. */
    @Override
    public Optional<HistoriaClinica> buscarPorPaciente(int idPaciente) {
        exigirSesion();
        Validador.validarIdPersistido(idPaciente, "El identificador de paciente");
        return MockDatabase.getInstance().buscarHistoriaPorPaciente(idPaciente);
    }

    /** Abre una nueva historia clínica para un paciente. */
    @Override
    public HistoriaClinica abrir(int idPaciente, String grupoSanguineo, String alergias,
                                 String antecedentesMedicos) {
        exigirSesion();
        Validador.validarIdPersistido(idPaciente, "El identificador de paciente");
        return MockDatabase.getInstance()
                .abrirHistoria(idPaciente, grupoSanguineo, alergias, antecedentesMedicos);
    }

    /** Actualiza la cabecera de la historia clínica de un paciente. */
    @Override
    public HistoriaClinica actualizarCabecera(int idPaciente, int idHistoriaClinica,
                                              String grupoSanguineo, String alergias, String antecedentesMedicos) {
        exigirSesion();
        Validador.validarIdPersistido(idPaciente, "El identificador de paciente");
        Validador.validarIdPersistido(idHistoriaClinica, "El identificador de historia clínica");
        return MockDatabase.getInstance().actualizarCabeceraHistoria(idPaciente,
                idHistoriaClinica, grupoSanguineo, alergias, antecedentesMedicos);
    }

    /** Anexa una nueva evolución clínica a la historia clínica de un paciente. */
    @Override
    public EvolucionClinica anexarEvolucion(int idPaciente, int idHistoriaClinica,
                                            String piezaDental, String diagnostico, String tratamientoRealizado,
                                            String observaciones) {
        UserSession.Identidad actor = exigirSesion();
        Validador.validarIdPersistido(idPaciente, "El identificador de paciente");
        Validador.validarIdPersistido(idHistoriaClinica, "El identificador de historia clínica");
        Odontologo autor = resolverAutor(actor);
        return MockDatabase.getInstance().anexarEvolucion(idPaciente, idHistoriaClinica,
                autor, piezaDental, diagnostico, tratamientoRealizado, observaciones);
    }

    /** Exige que la sesión esté activa y que el usuario tenga el rol de odontólogo. */
    private UserSession.Identidad exigirSesion() {
        return UserSession.getInstance().exigirRol(RolUsuario.Odontologo);
    }

    /** Resuelve el odontólogo autor de la acción a partir de la identidad del actor en sesión. */
    private Odontologo resolverAutor(UserSession.Identidad actor) {
        OptionalInt idAutor = MockDatabase.getInstance().buscarIdOdontologoActivo(actor.getIdPersona());
        if (idAutor.isEmpty()) {
            throw new ValidationException("La cuenta o el profesional no están habilitados para acceder "
                    + "a historias clínicas. Inicie sesión nuevamente.");
        }
        for (Odontologo odontologo : MockDatabase.getInstance().listarOdontologos(true)) {
            if (odontologo.getIdOdontologo() == idAutor.getAsInt()) {
                return odontologo;
            }
        }
        throw new ValidationException("El profesional autor ya no existe. Inicie sesión nuevamente.");
    }
}
