package com.cocab.dao.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.OptionalInt;

import com.cocab.dao.TurnoDAO;
import com.cocab.exception.ValidationException;
import com.cocab.mock.MockDatabase;
import com.cocab.model.odontologo.Odontologo;
import com.cocab.model.paciente.Paciente;
import com.cocab.model.seguridad.RolUsuario;
import com.cocab.model.turno.EstadoTurno;
import com.cocab.model.turno.Turno;
import com.cocab.util.AlgoritmosClinica;
import com.cocab.util.UserSession;
import com.cocab.util.Validador;

/**
 * Implementación de {@link TurnoDAO} sobre el repositorio en memoria {@link MockDatabase},
 * con las reglas de dominio (RN-02 de solapamiento, roles de agenda).
 * Al depender solo de la interfaz, la próxima entrega incorporará una base de datos sin modificar los controladores.
 */
public final class TurnoDAOImpl implements TurnoDAO {

    /** Lista los turnos dentro de un rango de fechas, filtrando por odontólogo y estado de actividad. */
    @Override
    public ArrayList<Turno> listar(LocalDate desde, LocalDate hasta, Integer idOdontologo,
                                   boolean incluirInactivos) {
        UserSession.Identidad actor = exigirSesionLectura();
        Validador.validarFechaSql(desde, "La fecha inicial");
        Validador.validarFechaSql(hasta, "La fecha final");
        long diferencia = ChronoUnit.DAYS.between(desde, hasta);
        if (diferencia < 0 || diferencia > 6) {
            throw new ValidationException("La agenda admite un rango inclusivo de uno a siete días.");
        }
        if (idOdontologo != null) {
            Validador.validarIdPersistido(idOdontologo, "El identificador del odontólogo");
        }
        Integer idPropio = resolverIdPropioSiOdontologo(actor);
        if (idPropio != null && idOdontologo != null && !idPropio.equals(idOdontologo)) {
            throw new ValidationException("Solo puede consultar su propia agenda.");
        }
        Integer filtro = idPropio == null ? idOdontologo : idPropio;
        ArrayList<Turno> turnos = MockDatabase.getInstance().listarTurnos(desde, hasta, filtro, incluirInactivos);
        return ordenarPorDia(turnos);
    }

    /** Lista todos los pacientes activos en el sistema. */
    @Override
    public ArrayList<Paciente> listarPacientesActivos() {
        exigirSesionSecretaria();
        return new PacienteDAOImpl().listar(false);
    }

    /** Lista todos los odontólogos, con opción de incluir los inactivos. */
    @Override
    public ArrayList<Odontologo> listarOdontologos(boolean incluirInactivos) {
        exigirSesionSecretaria();
        return new OdontologoDAOImpl().listar(incluirInactivos);
    }

    /** Registra un nuevo turno en el sistema. */
    @Override
    public Turno registrar(Turno nuevo) {
        exigirSesionSecretaria();
        Turno copia = copiar(nuevo, true);
        validarFechaYFranja(copia);
        return MockDatabase.getInstance().registrarTurno(copia);
    }

    /** Reprograma un turno existente, aplicando las validaciones correspondientes. */
    @Override
    public Turno reprogramar(Turno original, Turno cambios) {
        exigirSesionSecretaria();
        Turno esperado = copiar(original, false);
        Turno propuesto = copiar(cambios, false);
        exigirPendiente(esperado);
        exigirPacienteFijo(esperado, propuesto);
        validarFechaYFranja(propuesto);
        return MockDatabase.getInstance().reprogramarTurno(esperado, propuesto);
    }

    /** Cancela un turno pendiente en el sistema. */
    @Override
    public void cancelar(Turno original) {
        exigirSesionSecretaria();
        Turno esperado = copiar(original, false);
        exigirPendiente(esperado);
        MockDatabase.getInstance().cancelarTurno(esperado);
    }

    /** Exige que la sesión esté activa y que el usuario tenga el rol de lectura (secretaria u odontólogo). */
    private UserSession.Identidad exigirSesionLectura() {
        return UserSession.getInstance().exigirRol(RolUsuario.Secretaria, RolUsuario.Odontologo);
    }

    /** Exige que la sesión esté activa y que el usuario tenga el rol de secretaria. */
    private void exigirSesionSecretaria() {
        UserSession.getInstance().exigirRol(RolUsuario.Secretaria);
    }

    /** Resuelve el ID propio del odontólogo si el actor tiene ese rol; de lo contrario, devuelve null. */
    private Integer resolverIdPropioSiOdontologo(UserSession.Identidad actor) {
        if (actor.getRol() != RolUsuario.Odontologo) {
            return null;
        }
        OptionalInt idPropio = MockDatabase.getInstance().buscarIdOdontologoActivo(actor.getIdPersona());
        if (idPropio.isEmpty()) {
            throw new ValidationException("La cuenta o el profesional no están habilitados para consultar "
                    + "la agenda. Inicie sesión nuevamente.");
        }
        return idPropio.getAsInt();
    }

    /** Ordena una lista de turnos por día y hora, manteniendo la estabilidad de la inserción. */
    private ArrayList<Turno> ordenarPorDia(ArrayList<Turno> turnos) {
        ArrayList<Turno> ordenados = new ArrayList<>();
        ArrayList<Turno> delDia = new ArrayList<>();
        LocalDate fecha = null;
        for (Turno turno : turnos) {
            if (fecha != null && !fecha.equals(turno.getFecha())) {
                AlgoritmosClinica.ordenarTurnosPorHora(delDia);
                ordenados.addAll(delDia);
                delDia.clear();
            }
            fecha = turno.getFecha();
            delDia.add(turno);
        }
        // El repositorio entrega fechas ascendentes y desempate por ID; la inserción estable
        // ordena SOLO cada día por hora. No mezclar los días de una semana.
        AlgoritmosClinica.ordenarTurnosPorHora(delDia);
        ordenados.addAll(delDia);
        return ordenados;
    }

    /** Crea una copia de un turno, validando los campos según si es un alta o una modificación. */
    private static Turno copiar(Turno origen, boolean alta) {
        Validador.requerirNoNulo(origen, "El turno");
        int idTurno = origen.getIdTurno();
        EstadoTurno estado = Validador.requerirNoNulo(origen.getEstado(), "El estado del turno");
        if (alta) {
            if (idTurno != 0 || estado != EstadoTurno.Asignado) {
                throw new ValidationException("Un turno nuevo debe tener identificador cero y estado Asignado.");
            }
        } else {
            Validador.validarIdPersistido(idTurno, "El identificador del turno");
        }
        Paciente paciente = Validador.requerirNoNulo(origen.getPaciente(), "El paciente");
        Odontologo odontologo = Validador.requerirNoNulo(origen.getOdontologo(), "El odontólogo");
        Validador.validarIdPersistido(paciente.getIdPaciente(), "El identificador del paciente");
        Validador.validarIdPersistido(paciente.getIdPersona(), "La persona del paciente");
        Validador.validarIdPersistido(odontologo.getIdOdontologo(), "El identificador del odontólogo");
        Validador.validarIdPersistido(odontologo.getIdPersona(), "La persona del odontólogo");
        // Copias profundas administrativas; nunca leer ni copiar historiaClinica.
        Paciente copiaPaciente = new Paciente(paciente.getIdPaciente(), paciente.getIdPersona(),
                paciente.getDni(), paciente.getNombre(), paciente.getApellido(), paciente.getTelefono(),
                paciente.getEmail(), paciente.getDireccion(), paciente.getFechaNacimiento(),
                paciente.getObraSocial(), paciente.isEstadoActivo());
        Odontologo copiaOdontologo = new Odontologo(odontologo);
        return new Turno(idTurno, copiaPaciente, copiaOdontologo, origen.getFecha(), origen.getHoraInicio(),
                origen.getHoraFin(), origen.getMotivoConsulta(), estado);
    }

    /** Exige que el paciente de un turno no cambie durante la reprogramación. */
    private static void exigirPacienteFijo(Turno original, Turno cambios) {
        if (original.getIdTurno() != cambios.getIdTurno()
                || original.getPaciente().getIdPaciente() != cambios.getPaciente().getIdPaciente()
                || original.getPaciente().getIdPersona() != cambios.getPaciente().getIdPersona()) {
            throw new ValidationException("La reprogramación debe conservar el identificador del turno y su paciente.");
        }
    }

    /** Exige que un turno esté en estado pendiente (Asignado o Reprogramado) antes de reprogramar o cancelar. */
    private static void exigirPendiente(Turno turno) {
        if (turno.getEstado() != EstadoTurno.Asignado && turno.getEstado() != EstadoTurno.Reprogramado) {
            throw new ValidationException("Solo se pueden reprogramar o cancelar turnos Asignado o Reprogramado. "
                    + "Actualice la agenda antes de continuar.");
        }
    }

    /** Valida que la fecha y la franja horaria de un turno sean correctas. */
    private static void validarFechaYFranja(Turno turno) {
        Validador.validarFechaAsignacion(turno.getFecha());
        Validador.validarFranjaHoraria(turno.getHoraInicio(), turno.getHoraFin());
    }
}
